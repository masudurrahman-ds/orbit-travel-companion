plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.crowncouncil.orbit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crowncouncil.orbit"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0"
        // Backend URL. 10.0.2.2 is the emulator's alias for the computer running the backend.
        buildConfigField("String", "API_BASE_URL", "\"${project.findProperty("ORBIT_API_URL") ?: "http://10.0.2.2:8080"}\"")
        // Google Maps key: put MAPS_API_KEY=... in ~/.gradle/gradle.properties (never commit it).
        manifestPlaceholders["MAPS_API_KEY"] = project.findProperty("MAPS_API_KEY")?.toString() ?: ""
        // The built-in ORBIT Brain is always the guide. Set ORBIT_CLOUD_AI=true only if you also run the optional cloud guide on the backend.
        buildConfigField("boolean", "USE_CLOUD_AI", (project.findProperty("ORBIT_CLOUD_AI")?.toString() == "true").toString())
        buildConfigField("boolean", "MAPS_KEY_SET", (!project.findProperty("MAPS_API_KEY")?.toString().isNullOrBlank()).toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:6.1.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // Pinned LiteRT-LM runtime for optional on-device generative inference. Keep the version pinned
    // rather than using latest.release so Kotlin/KSP compatibility can be verified in CI.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
