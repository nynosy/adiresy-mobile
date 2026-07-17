import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

val releaseKeystorePath: String = localProps.getProperty("release.keystore.path", "")
val releaseKeystorePassword: String = localProps.getProperty("release.keystore.password", "")
val releaseKeyAlias: String = localProps.getProperty("release.key.alias", "")
val releaseKeyPassword: String = localProps.getProperty("release.key.password", "")
val hasReleaseSigning = releaseKeystorePath.isNotBlank()

// Version derived from git — a tagged commit always builds with a matching
// versionName, no manual "Bump version" commit required before tagging.
// Requires full history (not a shallow clone) to count commits / see tags.
fun gitOutput(vararg args: String): String =
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()

val gitVersionCode: Int = gitOutput("rev-list", "--count", "HEAD").toIntOrNull() ?: 1
val gitVersionName: String = gitOutput("describe", "--tags", "--always", "--dirty")
    .removePrefix("v")
    .ifBlank { "0.0.0" }

android {
    namespace = "org.github.nynosy.adiresy_mobile"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "org.github.nynosy.adiresy_mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = gitVersionCode
        versionName = gitVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // UI
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.fragment)

    // Map
    implementation(libs.maplibre)

    // Architecture — ViewModel + LiveData
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // Network — Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Local data — Room
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // Background work & encrypted prefs
    implementation(libs.work.runtime)
    implementation(libs.security.crypto)

    // QR code generation (address-link QR on the code card / code detail screens)
    implementation(libs.zxing.core)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
