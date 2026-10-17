plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.nexora.vpn"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nexora.vpn"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-test"
    }
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "NEXORA_API_URL", "\"\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}
dependencies {
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
