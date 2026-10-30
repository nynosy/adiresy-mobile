import java.util.Properties
import net.ltgt.gradle.errorprone.errorprone

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.errorprone)
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

android {
    namespace = "org.github.nynosy.adiresy_mobile"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    // AGP embeds a "Dependency metadata" block in the APK signing block by
    // default (Play Console SDK transparency). F-Droid's reproducible-build
    // checker rejects any unrecognized extra signing block, so it must be off.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "org.github.nynosy.adiresy_mobile"
        // Raised from 24 to 26 (Android 8.0) — Android Key Attestation (see
        // GitHub issue #5 / DeviceAuthManager) can't verify *which app* is
        // calling on API 24-25, only that some app holds the key, so those
        // devices can't be supported once attestation enforcement is on.
        minSdk = 26
        targetSdk = 36
        // Literal, not git-derived — F-Droid's Tags-based auto-updater greps
        // this file for the versionCode and versionName assignments below;
        // it can't resolve a computed or variable value. Bump both before
        // tagging a release: versionName must match the vX.Y.Z tag, and
        // versionCode must increase by at least 1.
        versionCode = 44
        versionName = "1.1.0"

        // Real devices are always arm64-v8a or armeabi-v7a; excluding x86/x86_64
        // (emulator-only) keeps the universal APK close to the budget-hardware
        // size target instead of bundling every ABI MapLibre ships.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

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
            // AGP refuses to run bundleRelease (the Play Store .aab task) while
            // ABI-split APK output is enabled (https://issuetracker.google.com/402800800).
            // Only the GitHub/F-Droid release flow (assembleRelease) needs these
            // per-ABI + universal APKs; Play's bundle handles ABI splitting itself.
            isEnable = gradle.startParameter.taskNames.none { it.contains("bundle", ignoreCase = true) }
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

    lint {
        // Existing issues are grandfathered in via the baseline below; only
        // newly introduced lint issues fail the build. Regenerate the
        // baseline with `./gradlew updateLintBaseline` after deliberately
        // fixing or accepting issues it currently hides.
        baseline = file("lint-baseline.xml")
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
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

    // Background work
    implementation(libs.work.runtime)

    // QR code generation (address-link QR on the code card / code detail screens)
    implementation(libs.zxing.core)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Code quality
    errorprone(libs.errorprone.core)
}

// AGP's per-variant annotationProcessor configurations (debugAnnotationProcessor,
// releaseAnnotationProcessor, ...) aren't standard Gradle source sets, so the
// errorprone plugin's usual auto-wiring skips them entirely; extend them by
// hand or the compiler plugin never ends up on javac's processor path.
configurations.matching { it.name.endsWith("AnnotationProcessor") }.configureEach {
    extendsFrom(configurations.getByName("errorprone"))
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        // The plugin only defaults this to true for tasks it discovers via
        // Gradle's SourceSetContainer; AGP's compileDebugJavaWithJavac etc.
        // aren't reachable that way, so without this the -Xplugin:ErrorProne
        // arg never gets added and the whole setup silently no-ops. (Forking
        // and the JDK 16+ --add-exports/--add-opens flags are handled
        // automatically by the plugin once this is enabled.)
        enabled.set(true)
        disableWarningsInGeneratedCode.set(true)
        // Room's generated DAO/database implementations live under here and
        // aren't ours to fix.
        excludedPaths.set(".*/build/generated/.*")
    }
}
