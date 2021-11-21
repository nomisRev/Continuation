import kotlinx.knit.KnitPluginExtension
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  dependencies {
    classpath("org.jetbrains.kotlinx:kotlinx-knit:0.2.3")
  }
}

plugins {
  kotlin("multiplatform") version "1.6.0" apply true
  alias(libs.plugins.kotest.multiplatform)
  alias(libs.plugins.arrowGradleConfig.multiplatform)
  alias(libs.plugins.arrowGradleConfig.formatter)
  alias(libs.plugins.dokka)
}

apply(plugin = "kotlinx-knit")

group "com.github.nomisrev"
version "1.0"

repositories {
  mavenCentral()
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(libs.kotlin.stdlibCommon)
        api(libs.arrow.core)
      }
    }
    commonTest {
      dependencies {
        implementation(libs.arrow.fx)
        implementation(libs.kotest.frameworkEngine)
        implementation(libs.kotest.assertionsCore)
        implementation(libs.kotest.property)
      }
    }
    jvmMain {
      dependencies {
        implementation(libs.kotlin.stdlibJDK8)
      }
    }
    jsMain {
      dependencies {
        implementation(libs.kotlin.stdlibJS)
      }
    }
    named("jvmTest") {
      dependencies {
        implementation(libs.kotest.runnerJUnit5)
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

  withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
  }
}
