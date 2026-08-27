import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.wanderwildwood.einkgo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wanderwildwood.einkgo"
        // The Kompakt runs Android 12 (API 31); nothing here needs anything newer.
        minSdk = 31
        targetSdk = 31
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            // The bundled engine is built for the Kompakt and nothing else.
            //noinspection ChromeOsAbiSupport
            abiFilters += "arm64-v8a"
        }
    }

    // A real keystore in signing/ signs every build type when it is present, so the
    // very first install is already release-signed and a later update can never hit
    // INSTALL_FAILED_UPDATE_INCOMPATIBLE. It is gitignored: a fresh clone falls back
    // to the default debug key rather than to a checked-in one, because a keystore
    // committed to a public repo is not a signing key, it is a formality.
    val signingPropertiesFile = rootProject.file("signing/signing.properties")
    val realSigningConfig = if (signingPropertiesFile.isFile) {
        val signingProperties = Properties().apply {
            signingPropertiesFile.inputStream().use(::load)
        }
        signingConfigs.create("real") {
            storeFile = rootProject.file("signing/signing.keystore")
            storePassword = signingProperties.getProperty("STORE_PASSWORD")
            keyAlias = signingProperties.getProperty("KEY_ALIAS")
            keyPassword = signingProperties.getProperty("KEY_PASSWORD")
        }
    } else {
        null
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            realSigningConfig?.let { signingConfig = it }
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            realSigningConfig?.let { signingConfig = it }
        }
    }

    packaging {
        jniLibs {
            // The engine is an executable, not a library: it has to be unpacked onto
            // disk in nativeLibraryDir for the app to be able to exec it.
            useLegacyPackaging = true
        }
    }

    lint {
        // This app is sideloaded onto a Kompakt and is not going to Google Play, whose
        // API-33 floor this otherwise trips. Targeting the OS the device actually runs
        // is deliberate: see minSdk above.
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        // The About screen shows the version it is actually running.
        buildConfig = true
    }

    sourceSets {
        named("main") {
            kotlin.srcDir("src/main/kotlin")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.mmd)

    testImplementation(libs.junit)
}
