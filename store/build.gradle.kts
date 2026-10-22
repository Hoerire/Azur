plugins {
    id("com.android.library") version "9.1.1"
}

android {
    namespace = "com.hoerire.tools.store"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // 必须与 :app 的 applicationId 完全一致：XValues.getAppTypeOf 靠它判定 Me/Local。
        // 一旦漂移 -> AppType.Unknown -> Transport.Unreachable -> 所有 settings 写入失败。
        buildConfigField("String", "APPLICATION_ID", "\"com.hoerire.tools\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":business:api"))
    implementation(project(":libs:logging"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
