plugins {
  kotlin("multiplatform") version "1.5.31" apply true
  id("io.kotest.multiplatform") version "5.0.0.5" apply true
}

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
        implementation(kotlin("stdlib-common"))
        implementation("io.arrow-kt:arrow-core:1.0.0")
      }
    }
    commonTest {
      dependencies {
        implementation("io.kotest:kotest-property:5.0.0.M3")
        implementation("io.kotest:kotest-framework-engine:5.0.0.M3")
        implementation("io.kotest:kotest-assertions-core:5.0.0.M3")
      }
    }
    named("jvmTest") {
      dependencies {
        implementation("io.kotest:kotest-runner-junit5:5.0.0.M3")
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}
