import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
}

val appVersion = "1.0.0"
group = "com.muzza.desktop"
version = appVersion

// -Ptarget=windows-x64 bundles the Windows skia library, so a Windows build can be made on Linux.
val target = (findProperty("target") as String?) ?: "current"

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

dependencies {
    when (target) {
        "windows-x64" -> implementation(compose.desktop.windows_x64)
        "linux-x64" -> implementation(compose.desktop.linux_x64)
        else -> implementation(compose.desktop.currentOs)
    }
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("net.jthink:jaudiotagger:3.0.1")
    testImplementation(kotlin("test"))
}

val genVersion by tasks.registering {
    val out = layout.buildDirectory.dir("generated/version")
    outputs.dir(out)
    inputs.property("v", appVersion)
    doLast {
        val f = out.get().file("com/muzza/desktop/BuildInfo.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("package com.muzza.desktop\n\nobject BuildInfo { const val VERSION = \"$appVersion\" }\n")
    }
}
kotlin.sourceSets["main"].kotlin.srcDir(genVersion)

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    compilerOptions.freeCompilerArgs.addAll(
        "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
    )
}

/** One runnable jar with everything inside (used by packaging/package.sh for Windows and Linux). */
val fatJar by tasks.registering(Jar::class) {
    archiveFileName.set("muzza-desktop-$target.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fat"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes("Main-Class" to "com.muzza.desktop.MainKt", "Implementation-Version" to appVersion) }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC", "META-INF/INDEX.LIST", "module-info.class")
    }
}

compose.desktop {
    application {
        mainClass = "com.muzza.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            packageName = "Muzza"
            packageVersion = appVersion
            description = "Offline music player"
            vendor = "Muzza"
            linux { iconFile.set(project.file("packaging/muzza-icon.png")) }
            windows { iconFile.set(project.file("packaging/muzza.ico")); menuGroup = "Muzza"; shortcut = true }
        }
    }
}
