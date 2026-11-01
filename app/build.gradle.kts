import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Local deployment opts in to a development certificate, never a production identity.
val localReleaseSigning = providers.gradleProperty("viewy.localReleaseSigning")
    .map { it.toBooleanStrict() }.getOrElse(false)
val releaseSigningFile = rootProject.file("signing.local.properties")
val releaseSigning = Properties().apply {
    if (!localReleaseSigning && releaseSigningFile.isFile) {
        releaseSigningFile.inputStream().use { load(it) }
        require(listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all {
            !getProperty(it).isNullOrBlank()
        }) { "signing.local.properties must define storeFile, storePassword, keyAlias and keyPassword." }
    }
}

android {
    namespace = "com.malagel.viewy"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.malagel.viewy"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (!releaseSigning.isEmpty) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            manifestPlaceholders["launcherLabel"] = "ViewyDebug"
        }
        release {
            isDebuggable = false
            manifestPlaceholders["launcherLabel"] = "Viewy"
            optimization {
                enable = true
            }
            signingConfig = when {
                localReleaseSigning -> signingConfigs.getByName("debug")
                !releaseSigning.isEmpty -> signingConfigs.getByName("release")
                else -> null // Unsigned unless signing was explicitly configured.
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
