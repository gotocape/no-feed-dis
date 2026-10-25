plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.haijiao.nofeeddis"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.haijiao.nofeeddis"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.3-ace"
    }

    buildTypes {
        debug {
            // 关掉 debuggable 之外的额外开销，保持包体干净
            isMinifyEnabled = false
        }
        release {
            // 自用版不做混淆：出问题时能直接看堆栈
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 自用，用 debug 签名也能装；如需正式签名再加 signingConfigs
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/*.kotlin_module",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json"
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
