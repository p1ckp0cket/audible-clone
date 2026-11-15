plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.allopen") version "1.9.22"
}

android {
    namespace = "com.audibleclone"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.audibleclone"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // BuildConfig for API base URL
        buildConfigField("String", "API_BASE_URL", "\"https://yourdomain.com/api/v1/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }

    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE,LICENSE.txt,NOTICE,NOTICE.txt}"
        }
    }

    // Test options
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            returnDefaultValues = true
        }
        execution {
            argument("androidx.test.orchestrator", "true")
        }
    }

    // All-open for Hilt testing
    allOpen {
        annotation("dagger.hilt.android.testing.HiltAndroidTest")
        annotation("dagger.hilt.android.testing.UninstallModules")
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.window:window:1.3.0")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.runtime:runtime-rxjava2")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.1")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.1")

    // Media3 / ExoPlayer - Full media playback support
    implementation("androidx.media3:media3-exoplayer:1.4.0")
    implementation("androidx.media3:media3-exoplayer-ui:1.4.0")
    implementation("androidx.media3:media3-session:1.4.0")
    implementation("androidx.media3:media3-controller:1.4.0")
    implementation("androidx.media3:media3-common:1.4.0")
    implementation("androidx.media3:media3-transformer:1.4.0")
    implementation("androidx.media3:media3-datasource:1.4.0")
    implementation("androidx.media3:media3-cache:1.4.0")
    implementation("com.google.android.exoplayer:extension-mediasession:2.20.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Database (Room)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Dependency Injection (Hilt)
    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Coroutines/Flow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-network-okhttp:2.5.0")

    // Preferences/DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager (background sync)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // Audio processing utilities
    implementation("com.github.wseemann:AudioBookReader:1.0.0")

    // Testing
    // Unit testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-inline:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test:rules:1.5.0")
    testImplementation("org.robolectric:robolectric:4.11")
    testImplementation("com.google.truth:truth:1.1.5")
    
    // Room testing
    testImplementation("androidx.room:room-testing:2.6.1")
    
    // DataStore testing
    testImplementation("androidx.datastore:datastore-preferences:1.1.1")
    testImplementation("androidx.datastore:datastore-testing:1.1.1")
    
    // Retrofit/OkHttp testing
    testImplementation("com.squareup.retrofit2:retrofit-mock:2.11.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    
    // Compose testing (unit)
    testImplementation("androidx.compose.ui:ui-test-junit4:1.5.11")
    testImplementation("androidx.compose.ui:ui-test-manifest:1.5.11")
    
    // Navigation testing
    testImplementation("androidx.navigation:navigation-testing:2.8.1")
    
    // Hilt testing
    testImplementation("com.google.dagger:hilt-android-testing:2.51")
    kaptTest("com.google.dagger:hilt-compiler:2.51")
    
    // Performance testing
    testImplementation("androidx.benchmark:benchmark-junit4:1.2.2")
    testImplementation("androidx.benchmark:benchmark-macro-junit4:1.2.2")
    
    // UI/Instrumentation testing
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-accessibility:3.5.1")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.11")
    androidTestImplementation("androidx.compose.ui:ui-test-manifest:1.5.11")
    androidTestImplementation("androidx.compose.material3:material3:1.2.1")
    androidTestImplementation("androidx.activity:activity-compose:1.9.0")
    androidTestImplementation("androidx.navigation:navigation-testing:2.8.1")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.51")
    kaptAndroidTest("com.google.dagger:hilt-compiler:2.51")
    androidTestImplementation("androidx.test:orchestrator:1.4.2")
    androidTestImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    androidTestImplementation("org.mockito:mockito-android:5.11.0")
    androidTestImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    androidTestImplementation("com.google.truth:truth:1.1.5")
    
    // Performance/Profiling
    androidTestImplementation("androidx.benchmark:benchmark-macro-junit4:1.2.2")
    androidTestImplementation("androidx.test.services:test-services:1.5.0")
    
    // Screenshots/Visual testing
    androidTestImplementation("com.facebook.screenshot-tests-for-android:core:0.14.0")
    androidTestImplementation("com.facebook.screenshot-tests-for-android:runner:0.14.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.5.11")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.5.11")
}