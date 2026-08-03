import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Reads the gitignored .env at the repo root (see .env.example) so the POI
// server's address never ends up in version control. Falls back to
// localhost when .env hasn't been created yet, so a fresh checkout still
// builds — it just won't be able to reach a POI server until you add one.
// Retrofit requires baseUrl to end in "/" (throws otherwise), so a missing
// trailing slash in .env is normalized here rather than crashing at runtime.
val serverBaseUrl: String = run {
    val envFile = rootProject.file(".env")
    val props = Properties()
    if (envFile.exists()) {
        envFile.inputStream().use { props.load(it) }
    }
    val raw = props.getProperty("SERVER_BASE_URL") ?: "http://localhost:8080/"
    if (raw.endsWith("/")) raw else "$raw/"
}

android {
    namespace = "com.planroute.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.planroute.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SERVER_BASE_URL", "\"$serverBaseUrl\"")
    }

    buildTypes {
        release {
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.osmdroid.android)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.ui.tooling)
}
