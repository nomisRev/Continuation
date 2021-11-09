plugins {
  kotlin("jvm")
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(rootProject)
  implementation(libs.arrow.fx)
  implementation(libs.kotest.assertionsCore)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.knit.test)
}

sourceSets.test {
  java.srcDirs("example", "test")
}
