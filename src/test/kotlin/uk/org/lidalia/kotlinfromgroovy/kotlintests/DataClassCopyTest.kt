
package uk.org.lidalia.kotlinfromgroovy.kotlintests

import org.junit.jupiter.api.Test
import uk.org.lidalia.kotlinfromgroovy.testsupport.DataClass
import uk.org.lidalia.kotlinfromgroovy.testsupport.DataClassWithCustomCopyMethod

@Suppress("CopyWithoutNamedArguments", "ktlint:standard:max-line-length", "ktlint:standard:argument-list-wrapping")
class DataClassCopyTest {

  private val dataClassInstance = DataClass("argument1", 2, true)

  @Test
  fun `can call copy using named arguments`() {

    assert(dataClassInstance.copy(argument1 = "new arg") == DataClass("new arg", 2, true))
    assert(dataClassInstance.copy(argument2 = 22) == DataClass("argument1", 22, true))
    assert(dataClassInstance.copy(argument3 = false) == DataClass("argument1", 2, false))
    assert(
      dataClassInstance.copy(argument1 = "new arg", argument2 = 22) ==
        DataClass("new arg", 22, true),
    )
    assert(
      dataClassInstance.copy(argument1 = "new arg", argument3 = false) ==
        DataClass("new arg", 2, false),
    )
    assert(
      dataClassInstance.copy(argument2 = 22, argument3 = false) ==
        DataClass("argument1", 22, false),
    )
    assert(
      dataClassInstance.copy(argument1 = "new arg", argument2 = 22, argument3 = false) ==
        DataClass("new arg", 22, false),
    )
  }

  @Test
  fun `can call copy using positional arguments`() {

    assert(dataClassInstance.copy() == DataClass("argument1", 2, true))
    assert(dataClassInstance.copy("new arg") == DataClass("new arg", 2, true))
    assert(dataClassInstance.copy("new arg", 22) == DataClass("new arg", 22, true))
    assert(dataClassInstance.copy("new arg", 22, false) == DataClass("new arg", 22, false))
  }

  @Test
  fun `can call copy on a class with a custom copy method`() {

    assert(
      DataClassWithCustomCopyMethod("argument1", 2).copy("new arg") ==
        DataClassWithCustomCopyMethod("new arg", 5),
    )
    assert(
      DataClassWithCustomCopyMethod("argument1", 2).copy("new arg", 3) ==
        DataClassWithCustomCopyMethod("new arg", 3),
    )
    assert(
      DataClassWithCustomCopyMethod("argument1", 2).copy(argument1 = "new arg") ==
        DataClassWithCustomCopyMethod("new arg", 5),
    )
    assert(
      DataClassWithCustomCopyMethod("argument1", 2).copy(argument2 = 3) ==
        DataClassWithCustomCopyMethod("argument1", 3),
    )
  }

  @Test
  fun `cannot call copy with wrong named argument`() {
//        dataClassInstance.copy(notThere = "new arg")
  }

  @Test
  fun `cannot call copy with wrong type of argument`() {
//        dataClassInstance.copy(argument1 = 2)
  }

  @Test
  fun `cannot call copy using positional arguments with wrong type of argument`() {
//        dataClassInstance.copy(2)
  }
}
