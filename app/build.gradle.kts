plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI passes -PciBuildNumber=<run number> so every build gets its own, ever-increasing
// version — makes it obvious which APK on the phone is actually newest.
val ciBuildNumber = (project.findProperty("ciBuildNumber") as String?)?.toIntOrNull() ?: 0

android {
    namespace = "com.ando.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ando.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = if (ciBuildNumber > 0) ciBuildNumber else 1
        versionName = if (ciBuildNumber > 0) "1.0.$ciBuildNumber" else "1.0-dev"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
