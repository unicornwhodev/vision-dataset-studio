plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "com.unicornwhodev.visiondatasetstudio"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.unicornwhodev.visiondatasetstudio"
    minSdk = 28
    targetSdk = 36
    testApplicationId = "com.unicornwhodev.visiondatasetstudio.test"
    versionCode = 8
    versionName = "4.2.0-rc3"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Debug uses Android's standard locally generated debug key. No private keystore is shipped.
  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.tensorflow.lite)
  // Save/Restore and gradient operators used by on-device training signatures.
  implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1") {
    exclude(group = "org.tensorflow", module = "tensorflow-lite")
    exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
  }
  implementation("androidx.work:work-runtime-ktx:2.11.2")
  implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

// Checked-in generated schemas are produced by KSP; never replace them with invented hashes.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
android.sourceSets.getByName("test").resources.srcDir("$projectDir/schemas")
dependencies {
    testImplementation("androidx.room:room-testing:2.7.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.10.0")
    androidTestImplementation("androidx.room:room-testing:2.7.0")
}

android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/src/test/resources")
