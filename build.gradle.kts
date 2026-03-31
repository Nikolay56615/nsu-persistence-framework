import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.the

plugins {
    kotlin("jvm") version "2.2.20"
}

group = "ru.nsu"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")
    testImplementation("org.assertj:assertj-core:3.27.3")
    implementation(kotlin("reflect"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}

val mainSourceSet = the<SourceSetContainer>().named("main")

val fatJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Assembles a self-contained jar with runtime dependencies."

    archiveBaseName.set("persistence-framework")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    dependsOn(tasks.named("classes"))
    dependsOn(configurations.runtimeClasspath)

    from(mainSourceSet.map { it.output })
    from(
        {
            configurations.runtimeClasspath.get()
                .filter { it.name.endsWith(".jar") }
                .map(::zipTree)
        }
    )

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.register<Copy>("prepareJarExample") {
    group = "build"
    description = "Copies the standalone library jar into the standalone example project."

    dependsOn(fatJar)
    from(fatJar.flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("examples/demo/libs"))
    rename { "persistence-framework-all.jar" }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

kotlin {
    jvmToolchain(21)
}
