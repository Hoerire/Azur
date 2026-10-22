plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
    id("com.google.devtools.ksp")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.hoerire.tools.zafiro.app"
    compileSdk = 37

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    defaultConfig {
        applicationId = "com.hoerire.tools"
        minSdk = 26
        targetSdk = 34
        versionCode = 32
        versionName = "1.8.1-fork"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "Zafiro"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        getByName("debug") {
            // Portable signing:
            //   1. ZAFIRO_KEYSTORE env var (absolute path)
            //   2. keystore/debug.keystore or keystore/release.keystore in the repo root
            //   3. /data/local/aitools/环境/debug.keystore (on-device build environment)
            //   4. fall back to the default debug keystore AGP generates under ~/.android
            val candidates = buildList {
                System.getenv("ZAFIRO_KEYSTORE")?.takeIf { it.isNotBlank() }?.let { add(project.file(it)) }
                add(rootProject.file("keystore/debug.keystore"))
                add(rootProject.file("keystore/release.keystore"))
                add(project.file("/data/local/aitools/环境/debug.keystore"))
            }
            candidates.firstOrNull { it.exists() }?.let { ks ->
                storeFile = ks
                storePassword = System.getenv("ZAFIRO_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("ZAFIRO_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("ZAFIRO_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    lint { checkReleaseBuilds = false }
}

dependencies {
    implementation(project(":agent-runtime"))
    implementation(project(":ui-kit"))
    implementation(project(":store"))
    implementation(project(":xsettings"))
    implementation(project(":remote-view"))
    implementation(project(":business:api"))
    implementation(project(":business:agent"))
    implementation(project(":business:files"))
    implementation(project(":business:notification"))
    implementation(project(":business:application"))
    implementation(project(":business:permission"))
    implementation(project(":libs:logging"))
    implementation(project(":libs:okia"))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.41.0")

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("androidx.compose.material3:material3:1.5.0-alpha22")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.8.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.room:room-testing:2.7.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.test:core:1.6.1")
}
