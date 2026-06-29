plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.aistudio.overread.bzvz"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.overread.bzvz"
    minSdk = 24
    targetSdk = 36
    versionCode = 18
    versionName = "1.3.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val sf = project.findProperty("OVERREAD_STORE_FILE") as String?
        ?: System.getenv("OVERREAD_STORE_FILE")
        ?: project.findProperty("android.injected.signing.store.file") as String?

      val sp = project.findProperty("OVERREAD_STORE_PASSWORD") as String?
        ?: System.getenv("OVERREAD_STORE_PASSWORD")
        ?: project.findProperty("android.injected.signing.store.password") as String?

      val ka = project.findProperty("OVERREAD_KEY_ALIAS") as String?
        ?: System.getenv("OVERREAD_KEY_ALIAS")
        ?: project.findProperty("android.injected.signing.key.alias") as String?

      val kp = project.findProperty("OVERREAD_KEY_PASSWORD") as String?
        ?: System.getenv("OVERREAD_KEY_PASSWORD")
        ?: project.findProperty("android.injected.signing.key.password") as String?

      val anySet = sf != null || sp != null || ka != null || kp != null

      if (anySet) {
        val missing = mutableListOf<String>()
        if (sf == null) missing.add("storeFile")
        if (sp == null) missing.add("storePassword")
        if (ka == null) missing.add("keyAlias")
        if (kp == null) missing.add("keyPassword")

        if (missing.isNotEmpty()) {
          throw GradleException("Incomplete release signing configuration. Missing: ${missing.joinToString(", ")}")
        }

        val sfFile = file(sf!!)
        if (!sfFile.exists()) {
          throw GradleException("Release keystore file not found at: $sf")
        }

        storeFile = sfFile
        storePassword = sp
        keyAlias = ka
        keyPassword = kp
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      
      val releaseSig = signingConfigs.getByName("release")
      if (releaseSig.storeFile != null) {
        signingConfig = releaseSig
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
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
  lint {
    checkReleaseBuilds = false
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
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
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  
  // ML Kit - Multi-script text recognition for webtoon translation
  implementation(libs.mlkit.text.recognition)
  implementation(libs.mlkit.text.recognition.chinese)
  implementation(libs.mlkit.text.recognition.japanese)
  implementation(libs.mlkit.text.recognition.korean)
  implementation(libs.mlkit.text.recognition.devanagari)
  implementation(libs.mlkit.language.id)
  implementation(libs.mlkit.translate)
  
  implementation(libs.kotlinx.coroutines.play.services)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
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

afterEvaluate {
  val taskNames = gradle.startParameter.taskNames
  val isPublishing = taskNames.any { it.contains("bundleRelease") || it.contains("assembleRelease") || it.contains("publish") }

  if (isPublishing) {
    val releaseSig = android.signingConfigs.getByName("release")
    val releaseBuildType = android.buildTypes.getByName("release")

    val sf = project.findProperty("OVERREAD_STORE_FILE") as String? ?: System.getenv("OVERREAD_STORE_FILE") ?: project.findProperty("android.injected.signing.store.file") as String?
    val sp = project.findProperty("OVERREAD_STORE_PASSWORD") as String? ?: System.getenv("OVERREAD_STORE_PASSWORD") ?: project.findProperty("android.injected.signing.store.password") as String?
    val ka = project.findProperty("OVERREAD_KEY_ALIAS") as String? ?: System.getenv("OVERREAD_KEY_ALIAS") ?: project.findProperty("android.injected.signing.key.alias") as String?
    val kp = project.findProperty("OVERREAD_KEY_PASSWORD") as String? ?: System.getenv("OVERREAD_KEY_PASSWORD") ?: project.findProperty("android.injected.signing.key.password") as String?

    println("--- PUBLISH DIAGNOSTICS ---")
    println("has OVERREAD_STORE_FILE: ${sf != null}")
    println("has OVERREAD_STORE_PASSWORD: ${sp != null}")
    println("has OVERREAD_KEY_ALIAS: ${ka != null}")
    println("has OVERREAD_KEY_PASSWORD: ${kp != null}")

    val isReleaseConfigApplied = (releaseBuildType.signingConfig?.name == "release")
    val storeFileExists = (releaseSig.storeFile?.exists() == true)

    println("release signingConfig applied: $isReleaseConfigApplied")
    println("release storeFile exists: $storeFileExists")
    println("---------------------------")

    if (!isReleaseConfigApplied || !storeFileExists) {
      println("WARNING: release publishing requires upload/release signing credentials. Please provide valid OVERREAD_* or android.injected.signing.* properties in the publishing environment.")
    }
  }
}
