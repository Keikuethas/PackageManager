plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.jsoizo:kotlin-csv:2.0.0")
    implementation("de.brudaswen.kotlinx.serialization:kotlinx-serialization-csv:3.2.2")
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    archiveVersion.set("")
}

application {
    applicationName = "PackageManager"
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Exec>("customJpackage") {
    dependsOn("installDist")

    val javaToolchains = project.extensions.getByType(JavaToolchainService::class.java)
    val jdkLauncher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    }.get()
    val jpackageExec = jdkLauncher.metadata.installationPath.dir("bin")
        .file("jpackage").asFile.absolutePath

    val installDir = layout.buildDirectory
        .dir("install/${application.applicationName}").get().asFile
    val libDir = installDir.resolve("lib")
    workingDir = libDir

    val outputDir = layout.buildDirectory.dir("jpackage").get().asFile
    outputDir.mkdirs()

    commandLine(
        jpackageExec,
        "--win-console",
        "--type", "app-image",
        "--name", application.applicationName,
        "--input", ".",
        "--main-jar", "${application.applicationName}.jar",
        "--main-class", "dev.keikuethas.MainKt",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--dest", outputDir.absolutePath
    )
}