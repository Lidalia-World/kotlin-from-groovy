package uk.org.lidalia.kotlinfromgroovy

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.concurrent.ConcurrentHashMap

object ReifiedBridgeGenerator {

  private val cache = ConcurrentHashMap<String, Class<*>>()

  fun callReifiedStatic(
    declaringClass: Class<*>,
    methodName: String,
    reifiedTypes: Array<Class<*>>,
    args: Array<Any?>,
  ): Any? {
    val bridgeClass = getOrCreateBridge(declaringClass)
    val method = bridgeClass.methods.first { m ->
      m.name == methodName && m.parameterCount == reifiedTypes.size + args.size
    }
    val allArgs = arrayOfNulls<Any>(reifiedTypes.size + args.size)
    reifiedTypes.forEachIndexed { i, c -> allArgs[i] = c }
    args.forEachIndexed { i, a -> allArgs[reifiedTypes.size + i] = a }
    return method.invoke(null, *allArgs)
  }

  fun callReifiedInstance(
    target: Any,
    methodName: String,
    reifiedTypes: Array<Class<*>>,
    args: Array<Any?>,
  ): Any? {
    val bridgeClass = getOrCreateBridge(target.javaClass)
    val expectedParamCount = 1 + reifiedTypes.size + args.size // instance + classes + args
    val method = bridgeClass.methods.first { m ->
      m.name == methodName && m.parameterCount == expectedParamCount
    }
    val allArgs = arrayOfNulls<Any>(expectedParamCount)
    allArgs[0] = target
    reifiedTypes.forEachIndexed { i, c -> allArgs[1 + i] = c }
    args.forEachIndexed { i, a -> allArgs[1 + reifiedTypes.size + i] = a }
    return method.invoke(null, *allArgs)
  }

  private fun getOrCreateBridge(declaringClass: Class<*>): Class<*> {
    val key = declaringClass.name
    return cache.getOrPut(key) { generateBridgeClass(declaringClass) }
  }

  private fun generateBridgeClass(declaringClass: Class<*>): Class<*> {
    val classNode = readClassNode(declaringClass)
    val bridgeClassName = "${classNode.name}\$ReifiedBridge"
    val bridgeNode = ClassNode().apply {
      version = classNode.version
      access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL
      name = bridgeClassName
      superName = "java/lang/Object"
    }

    classNode.methods
      .filter { it.isSyntheticReified() }
      .forEach { method ->
        bridgeNode.methods.add(patchMethod(method, classNode.name))
      }

    val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
    bridgeNode.accept(writer)
    val bytecode = writer.toByteArray()

    return BridgeClassLoader(declaringClass.classLoader).defineClass(
      bridgeClassName.replace('/', '.'),
      bytecode,
    )
  }

  private fun readClassNode(clazz: Class<*>): ClassNode {
    val classNode = ClassNode()
    val resourceName = clazz.name.replace('.', '/') + ".class"
    clazz.classLoader.getResourceAsStream(resourceName)!!.use { input ->
      ClassReader(input).accept(classNode, 0)
    }
    return classNode
  }

  private fun MethodNode.isSyntheticReified(): Boolean =
    (access and Opcodes.ACC_SYNTHETIC) != 0 && instructions.any { insn ->
      insn is MethodInsnNode &&
        insn.owner == "kotlin/jvm/internal/Intrinsics" &&
        insn.name == "reifiedOperationMarker"
    }

  private fun patchMethod(original: MethodNode, ownerInternalName: String): MethodNode {
    val reifiedParams = findReifiedTypeParams(original)
    val originalType = Type.getMethodType(original.desc)
    val originalArgTypes = originalType.argumentTypes
    val classType = Type.getType(Class::class.java)
    val isStatic = (original.access and Opcodes.ACC_STATIC) != 0
    val classParamBaseIndex = if (isStatic) 0 else 1
    val localShift = reifiedParams.size

    val instancePrefix = if (isStatic) {
      emptyArray()
    } else {
      arrayOf(Type.getObjectType(ownerInternalName))
    }
    val newArgTypes = instancePrefix + Array(reifiedParams.size) { classType } + originalArgTypes
    val newDesc = Type.getMethodDescriptor(originalType.returnType, *newArgTypes)

    // Build label map for cloning
    val labelMap = buildLabelMap(original)

    // Build type param name -> local variable index
    val typeParamToLocal = reifiedParams.withIndex()
      .associate { (index, name) -> name to classParamBaseIndex + index }

    val patched = MethodNode(
      Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
      original.name,
      newDesc,
      null,
      null,
    )

    val iter = original.instructions.iterator()
    while (iter.hasNext()) {
      val insn = iter.next()
      when {
        isMarkerPushSequenceStart(insn) -> {
          // This is the iconst_N that starts a marker sequence.
          // Skip it — we'll handle the whole sequence when we hit the invokestatic.
        }

        isMarkerLdc(insn) -> {
          // This is the ldc "T" in the marker sequence. Skip it too.
        }

        isReifiedMarkerCall(insn) -> {
          val markerType = getMarkerType(insn)
          val typeParamName = getMarkerTypeParam(insn)
          val classLocal = typeParamToLocal[typeParamName]
            ?: error("Unknown type param: $typeParamName")

          // Consume and patch the instruction after the marker
          val next = if (iter.hasNext()) iter.next() else null
          patchMarkerTarget(patched, markerType, classLocal, next)
        }

        else -> {
          val cloned = insn.clone(labelMap)
          shiftLocals(cloned, classParamBaseIndex, localShift)
          patched.instructions.add(cloned)
        }
      }
    }

    patched.maxStack = original.maxStack + 2
    patched.maxLocals = original.maxLocals + localShift

    return patched
  }

  private fun patchMarkerTarget(
    patched: MethodNode,
    markerType: Int,
    classLocal: Int,
    target: AbstractInsnNode?,
  ) {
    when (markerType) {
      4 -> {
        // T::class.java — replace `ldc Object.class` with `aload classParam`
        patched.instructions.add(VarInsnNode(Opcodes.ALOAD, classLocal))
      }

      3 -> {
        // is T — replace `instanceof Object` with classParam.isInstance(value)
        // Stack before: ..., objectref
        patched.instructions.add(VarInsnNode(Opcodes.ALOAD, classLocal))
        patched.instructions.add(InsnNode(Opcodes.SWAP))
        patched.instructions.add(
          MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            "java/lang/Class",
            "isInstance",
            "(Ljava/lang/Object;)Z",
            false,
          ),
        )
      }

      1 -> {
        // as T — replace `checkcast Object` with classParam.cast(value)
        // Stack before: ..., objectref
        patched.instructions.add(VarInsnNode(Opcodes.ALOAD, classLocal))
        patched.instructions.add(InsnNode(Opcodes.SWAP))
        patched.instructions.add(
          MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            "java/lang/Class",
            "cast",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            false,
          ),
        )
      }
    }
  }

  private fun isMarkerPushSequenceStart(insn: AbstractInsnNode): Boolean {
    // Check if this iconst_N is followed by ldc "X", invokestatic reifiedOperationMarker
    if (insn.opcode !in Opcodes.ICONST_0..Opcodes.ICONST_5) return false
    val next = skipNonCode(insn.next) ?: return false
    if (next !is LdcInsnNode || next.cst !is String) return false
    val afterLdc = skipNonCode(next.next) ?: return false
    return isReifiedMarkerCall(afterLdc)
  }

  private fun isMarkerLdc(insn: AbstractInsnNode): Boolean {
    if (insn !is LdcInsnNode || insn.cst !is String) return false
    val next = skipNonCode(insn.next) ?: return false
    return isReifiedMarkerCall(next)
  }

  private fun skipNonCode(start: AbstractInsnNode?): AbstractInsnNode? {
    var current = start
    while (current != null && current.opcode == -1) {
      current = current.next
    }
    return current
  }

  private fun buildLabelMap(method: MethodNode): Map<LabelNode, LabelNode> {
    val map = mutableMapOf<LabelNode, LabelNode>()
    val iter = method.instructions.iterator()
    while (iter.hasNext()) {
      val insn = iter.next()
      if (insn is LabelNode) {
        map[insn] = LabelNode()
      }
    }
    return map
  }

  private fun shiftLocals(
    insn: AbstractInsnNode,
    baseIndex: Int,
    shift: Int,
  ) {
    when (insn) {
      is VarInsnNode -> {
        if (insn.`var` >= baseIndex) insn.`var` += shift
      }

      is IincInsnNode -> {
        if (insn.`var` >= baseIndex) insn.`var` += shift
      }
    }
  }

  private fun findReifiedTypeParams(method: MethodNode): List<String> {
    val params = mutableListOf<String>()
    val iter = method.instructions.iterator()
    while (iter.hasNext()) {
      val insn = iter.next()
      if (isReifiedMarkerCall(insn)) {
        val name = getMarkerTypeParam(insn)
        if (name !in params) {
          params.add(name)
        }
      }
    }
    return params
  }

  private fun isReifiedMarkerCall(insn: AbstractInsnNode): Boolean = insn is MethodInsnNode &&
    insn.opcode == Opcodes.INVOKESTATIC &&
    insn.owner == "kotlin/jvm/internal/Intrinsics" &&
    insn.name == "reifiedOperationMarker"

  private fun getMarkerType(insn: AbstractInsnNode): Int {
    val ldc = findPreviousCode(insn)
    val iconst = findPreviousCode(ldc)
    return when (iconst?.opcode) {
      Opcodes.ICONST_0 -> 0
      Opcodes.ICONST_1 -> 1
      Opcodes.ICONST_2 -> 2
      Opcodes.ICONST_3 -> 3
      Opcodes.ICONST_4 -> 4
      Opcodes.ICONST_5 -> 5
      else -> -1
    }
  }

  private fun getMarkerTypeParam(insn: AbstractInsnNode): String {
    val ldc = findPreviousCode(insn) as LdcInsnNode
    return ldc.cst as String
  }

  private fun findPreviousCode(insn: AbstractInsnNode?): AbstractInsnNode? {
    var current = insn?.previous
    while (current != null && current.opcode == -1) {
      current = current.previous
    }
    return current
  }

  private class BridgeClassLoader(parent: ClassLoader) : ClassLoader(parent) {
    fun defineClass(name: String, bytecode: ByteArray): Class<*> =
      defineClass(name, bytecode, 0, bytecode.size)
  }
}
