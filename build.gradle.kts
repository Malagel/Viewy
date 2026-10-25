buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Upgrade AGP's built-in Kotlin compiler; keep aligned with the Compose plugin.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
