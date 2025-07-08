pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io")}
        maven { url = uri("https://plugins.gradle.org/m2/")}
    }
}

rootProject.name = "Milko"
include(":app")
