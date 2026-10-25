plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.videopocket.probe"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "org.videopocket.probe"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "0.3.2"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
        buildConfigField("boolean", "ENABLE_TORRENT", "true")
    }

    val keystorePropertiesFile = rootProject.file("local.properties")
    val keystoreProperties = java.util.Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(java.io.FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            val keyPath = System.getenv("KEYSTORE_PATH") ?: keystoreProperties.getProperty("release.keystorePath")
            val keyPass = System.getenv("KEYSTORE_PASSWORD") ?: keystoreProperties.getProperty("release.keystorePassword")
            val alias = System.getenv("KEY_ALIAS") ?: keystoreProperties.getProperty("release.keyAlias")
            val aliasPass = System.getenv("KEY_PASSWORD") ?: keystoreProperties.getProperty("release.keyPassword")

            if (!keyPath.isNullOrEmpty() && file(keyPath).exists()) {
                storeFile = file(keyPath)
                storePassword = keyPass
                keyAlias = alias
                keyPassword = aliasPass
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            }
        }
    }

    bundle {
        language {
            enableSplit = false // Keeps Arabic and English bundled for offline in-app language switching
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // yt-dlp + FFmpeg runtime
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("commons-io:commons-io:2.18.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestUtil("androidx.test.services:test-services:1.5.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

android { testOptions { unitTests.isIncludeAndroidResources = true } }
