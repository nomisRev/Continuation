plugins {
  kotlin("jvm")
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(rootProject)
  implementation("io.arrow-kt:arrow-fx-coroutines:1.0.0")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
  testImplementation("org.jetbrains.kotlinx:kotlinx-knit-test:0.2.3")
}

sourceSets.test {
  java.srcDirs("example", "test")
}
