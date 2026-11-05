plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing credentials come from ~/.gradle/gradle.properties or environment variables, never the repo.
fun signingValue(name: String): String? =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull

val releaseKeystore = signingValue("BUILDERTIMER_KEYSTORE")

android {
    namespace = "dev.ww40.buildertimer"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.ww40.buildertimer"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "0.1.12"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = signingValue("BUILDERTIMER_KEYSTORE_PASSWORD")
                keyAlias = signingValue("BUILDERTIMER_KEY_ALIAS")
                keyPassword = signingValue("BUILDERTIMER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.glance.appwidget)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
