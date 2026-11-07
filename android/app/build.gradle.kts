plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.jettaxp.steamd"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jettaxp.steamd"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("STEAMD_VERSION_CODE") ?: "1").toInt()
        versionName = "0.2.0"
    }

    val keystore = System.getenv("STEAMD_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storeType = "PKCS12"
                storePassword = System.getenv("STEAMD_KEYSTORE_PASSWORD")
                keyAlias = "steamd"
                keyPassword = System.getenv("STEAMD_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    packaging {
        resources {
            excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
        }
    }
}

dependencies {
    implementation(project(":patcher"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.work)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.material)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
