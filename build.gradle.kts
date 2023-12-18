import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.gundy.semver4j.model.Version

plugins {
  alias(libs.plugins.kotlin)
  `java-library`
  groovy

  alias(libs.plugins.dependencyAnalysis)
  alias(libs.plugins.kotlinter)
  alias(libs.plugins.taskTree)
  alias(libs.plugins.versions)
}

group = "uk.org.lidalia"

buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath(libs.semver4j)
  }
}

repositories {
  mavenCentral()
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  api(libs.groovy)
  runtimeOnly(libs.groovy.all)
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.spock.core)

  modules {
    module("org.hamcrest:hamcrest-core") {
      replacedBy("org.hamcrest:hamcrest")
    }
    module("org.hamcrest:hamcrest-library") {
      replacedBy("org.hamcrest:hamcrest")
    }
  }
}

dependencyLocking {
  lockAllConfigurations()
}

testing {
  suites {
    @Suppress("UnstableApiUsage")
    val test by getting(JvmTestSuite::class) {
      useJUnitJupiter()
    }
  }
}

val compileGroovy by tasks.named<GroovyCompile>("compileGroovy") {
  classpath += files(tasks.compileKotlin.get().destinationDirectory)
}

val compileTestGroovy by tasks.named<GroovyCompile>("compileTestGroovy") {
  classpath += files(tasks.compileTestKotlin.get().destinationDirectory)
}

kotlinter {
  reporters = arrayOf("checkstyle", "plain", "html")
}

tasks {
  check {
    dependsOn("buildHealth")
    dependsOn("installKotlinterPrePushHook")
  }
}

dependencyAnalysis {
  issues {
    // configure for all projects
    all {
      // set behavior for all issue types
      onAny {
        severity("fail")
        exclude("org.jetbrains.kotlin:kotlin-stdlib")
      }
    }
  }
}

tasks.withType<DependencyUpdatesTask> {
  rejectVersionIf {
    candidate.version.isPreRelease()
  }
}

fun String.isPreRelease(): Boolean = try {
  Version.fromString(this).preReleaseIdentifiers.isNotEmpty()
} catch (e: IllegalArgumentException) {
  false
}
