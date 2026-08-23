import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kspPlugin)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.codpast.player"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.codpast.player"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }

        buildConfigField(
            "String",
            "PODCAST_API_KEY",
            "\"${properties.getProperty("PODCAST_API_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "PODCAST_API_SECRET",
            "\"${properties.getProperty("PODCAST_API_SECRET", "")}\""
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true // 3. Enable BuildConfig generation
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}


dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Splash Screen
    implementation(libs.androidx.core.splashscreen)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.rssparser)
    implementation(libs.coroutines.guava)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.coil.compose)

// Compose Extended Icons
    implementation(libs.androidx.material.icons.extended)

    implementation("com.google.guava:guava:33.7.0-android")

    // WorkManager & Hilt Work Integration
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}