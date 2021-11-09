import kotlinx.knit.KnitPluginExtension
import org.jetbrains.dokka.gradle.DokkaTask

buildscript {
  dependencies {
    classpath("org.jetbrains.kotlinx:kotlinx-knit:0.2.3")
  }
}

plugins {
  kotlin("multiplatform") version "1.5.31" apply true
  id("io.kotest.multiplatform") version "5.0.0.5" apply true
  id("org.jetbrains.dokka") version "1.5.30" apply true
}

apply(plugin = "kotlinx-knit")

group "com.github.nomisrev"
version "1.0"

repositories {
  mavenCentral()
}

kotlin {
  jvm()

  sourceSets {
    commonMain {
      dependencies {
        api(kotlin("stdlib-common"))
        api("io.arrow-kt:arrow-core:1.0.0")
      }
    }
    commonTest {
      dependencies {
        implementation("io.kotest:kotest-property:5.0.0.M3")
        implementation("io.kotest:kotest-framework-engine:5.0.0.M3")
        implementation("io.kotest:kotest-assertions-core:5.0.0.M3")
        implementation("io.arrow-kt:arrow-fx-coroutines:1.0.0")
      }
    }
    named("jvmTest") {
      dependencies {
        implementation("io.kotest:kotest-runner-junit5:5.0.0.M3")
      }
    }
  }
}

configure<KnitPluginExtension> {
  siteRoot = "https://nomisrev.github.io/Continuation/"
}

tasks {
  withType<DokkaTask>().configureEach {
    outputDirectory.set(rootDir.resolve("docs"))
    moduleName.set("Cont")
    dokkaSourceSets {
      named("commonMain") {
        includes.from("README.md")
        perPackageOption {
          matchingRegex.set(".*\\.internal.*")
          suppress.set(true)
        }
        sourceLink {
          localDirectory.set(file("src/commonMain/kotlin"))
          remoteUrl.set(uri("https://github.com/nomisRev/Continuation/tree/main/src/commonMain/kotlin").toURL())
          remoteLineSuffix.set("#L")
        }
      }
    }
  }

  getByName("knitPrepare").dependsOn(getTasksByName("dokka", true))

  withType<Test>().configureEach {
    useJUnitPlatform()
  }
}
