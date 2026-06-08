plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.miafetta.statussync"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.miafetta.statussync"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "1.3.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
}
