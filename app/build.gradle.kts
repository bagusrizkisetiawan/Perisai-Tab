plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "id.co.alphanusa.perisaitab"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "id.co.alphanusa.perisaitab"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // URL default backend. Bisa diubah pengguna lewat layar Settings.
        // Sesuaikan nilai di bawah dengan environment produksi Anda.
        buildConfigField("String", "BASE_URL", "\"https://api.digicx.id\"")
        buildConfigField(
            "String",
            "CENTRIFUGO_WEBSOCKET_URL",
            "\"wss://centrifugo.digicx.id/connection/websocket\""
        )
        buildConfigField("String", "RTMP_URL", "\"rtmp://rtmp.digicx.id/live\"")
        buildConfigField("String", "LIVEKIT_URL", "\"wss://livekit.digicx.id\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.1")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Secure Storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("io.github.centrifugal:centrifuge-java:0.2.2")

    implementation("com.google.android.gms:play-services-location:21.0.1")

    implementation("com.github.pedroSG94.RootEncoder:library:2.5.0")

    // Icon
    implementation ("androidx.compose.material:material-icons-extended")

    implementation("com.huawei.hms:location:6.12.0.300")

    implementation("dev.chrisbanes.haze:haze:0.7.3")
    implementation("dev.chrisbanes.haze:haze-materials:0.7.3")

    implementation("io.livekit:livekit-android-compose-components:2.3.0")
    implementation("io.livekit:livekit-android:2.3.0")

    // osm droid map
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // libVLC (pemutar video / RTSP/RTMP player) - dari Maven Central
    // Versi disamakan dengan project TestGoPro yang sudah terbukti jalan.
    implementation("org.videolan.android:libvlc-all:3.7.4")

    //splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")
}