plugins { alias(libs.plugins.android.test) }

android {
    namespace = "com.unicornwhodev.visiondatasetstudio.releaseqa"
    compileSdk = 36
    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    // AndroidX lives in its own test process; R8 cannot remove its dependencies from the app.
    experimentalProperties["android.experimental.self-instrumenting"] = true
    buildTypes {
        create("release") {
            isDebuggable = true // Test APK only; the installed app must remain non-debuggable.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
androidComponents { beforeVariants(selector().withBuildType("debug")) { it.enable = false } }

dependencies {
    implementation(libs.androidx.runner)
    implementation(libs.androidx.junit)
    implementation("androidx.test.uiautomator:uiautomator:2.4.0")
}
