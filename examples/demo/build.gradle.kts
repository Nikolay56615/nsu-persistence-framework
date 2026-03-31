plugins {
    kotlin("jvm") version "2.2.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("libs/persistence-framework-all.jar"))
}

application {
    mainClass = "demo.DemoKt"
}

kotlin {
    jvmToolchain(21)
}
