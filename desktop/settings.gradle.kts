// Muzza Desktop is its own small Gradle build, so it builds without the Android SDK.
// Build it with the root wrapper:  ../gradlew -p desktop fatJar
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
rootProject.name = "MuzzaDesktop"
