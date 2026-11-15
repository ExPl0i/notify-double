plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.notifydouble.receiver"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.rolymq.notifydouble"
        minSdk = 31
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.0"
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
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
