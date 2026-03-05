plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.vishal.vibeplayer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.vishal.vibeplayer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Standard Android libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("androidx.media:media:1.6.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // --- NEW LINES ADDED BELOW ---

    // 1. Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // 2. Media3 (Music Player Engine)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // 3. Coil (Album Art Loader)
    implementation(libs.coil.kt)

    // 4. Retrofit (Online Streaming)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)

    // Retrofit & Gson for Online Streaming API
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Firebase Bill of Materials (BoM) - Automatically manages versions!
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

    // Firebase Authentication (For user login)
    implementation("com.google.firebase:firebase-auth")

    // Cloud Firestore (The NoSQL database where we will save Playlists and Stats!)
    implementation("com.google.firebase:firebase-firestore")

    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Glide for loading internet images
    implementation("com.github.bumptech.glide:glide:4.16.0")
}