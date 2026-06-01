plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "br.com.adsbanish"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.adsbanish"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile     = file(System.getenv("KEYSTORE_PATH") ?: "adsbanish.keystore")
            storePassword = System.getenv("STORE_PASSWORD") ?: ""
            keyAlias      = System.getenv("KEY_ALIAS")      ?: ""
            keyPassword   = System.getenv("KEY_PASSWORD")   ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig   = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures { compose = true; buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.viewmodel.compose)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
