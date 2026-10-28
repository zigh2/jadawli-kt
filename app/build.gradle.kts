plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val ks: String? = System.getenv("JD_KEYSTORE")

android {
    namespace = "com.jadawli.kt"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jadawli.kt"
        minSdk = 26
        targetSdk = 35
        versionCode = run
        versionName = "0.1.$run"
    }

    signingConfigs {
        if (ks != null) {
            create("rel") {
                storeFile = file(ks)
                storePassword = System.getenv("JD_STORE_PASS")
                keyAlias = System.getenv("JD_KEY_ALIAS")
                keyPassword = System.getenv("JD_KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (ks != null) signingConfigs.getByName("rel") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // فك تشفير النسخ الاحتياطية (zip بتشفير AES-256)
    implementation("net.lingala.zip4j:zip4j:2.11.5")
}
