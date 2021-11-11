import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  // TODO this can be MPP
  kotlin("jvm")
}

repositories {
  mavenCentral()
}

dependencies {
  testImplementation(rootProject)
  testImplementation(libs.arrow.fx)
  testImplementation(libs.kotest.assertionsCore)
  testImplementation(libs.kotest.property)
  testImplementation(libs.arrow.core.test)
  testImplementation(libs.kotest.runnerJUnit5)
  testImplementation(libs.kotest.frameworkEngine)
}

sourceSets.test {
  java.srcDirs("example", "test")
}

tasks {
  withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
      setExceptionFormat("full")
      setEvents(listOf("passed", "skipped", "failed", "standardOut", "standardError"))
    }
  }

  withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
  }
}
