import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The Play upload key, from the gitignored keystore.properties at the repo root.
val uploadKey = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use(::load) } }

// Internet-sideloaded notification listeners may be blocked by Play Protect. This option
// removes the service declaration and its runtime access for a reduced-permission APK.
val notificationAccessEnabled = providers.gradleProperty("notificationAccess").map { it.toBooleanStrict() }.getOrElse(true)

android {
    namespace = "dev.omalauncher"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "dev.omalauncher"
        minSdk = 29
        targetSdk = 37
        versionCode = 10
        versionName = if (notificationAccessEnabled) "0.5.0" else "0.5.0-no-notification-access"
        buildConfigField("boolean", "NOTIFICATION_ACCESS_ENABLED", notificationAccessEnabled.toString())
        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures { buildConfig = true }

    if (!notificationAccessEnabled) {
        sourceSets.getByName("debug").manifest.srcFile("src/noNotificationAccess/AndroidManifest.xml")
        sourceSets.getByName("release").manifest.srcFile("src/noNotificationAccess/AndroidManifest.xml")
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }

    packaging {
        resources.merges += setOf("META-INF/LICENSE.md", "META-INF/NOTICE.md")
        jniLibs {
            // Extract .so files to nativeLibraryDir: proot is exec'd from there.
            useLegacyPackaging = true
            keepDebugSymbols += listOf("**/libproot.so", "**/libproot-loader.so")
        }
    }

    // android.util.Log and friends are no-ops in JVM tests instead of throwing.
    testOptions { unitTests.isReturnDefaultValues = true }

    signingConfigs {
        create("release") {
            uploadKey?.let {
                storeFile = rootProject.file(it.getProperty("storeFile"))
                storePassword = it.getProperty("storePassword")
                keyAlias = it.getProperty("keyAlias")
                keyPassword = it.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Shrinks the APK and drops what the app doesn't use, like the owl module's unused parts.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (uploadKey != null) "release" else "debug")
        }
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("app/compose-stability.conf"))
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.commons.compress)
    implementation(libs.bcpg)
    implementation(libs.androidsvg)
    implementation(libs.zxing.core)
    implementation(project(":owl"))
    testImplementation(libs.junit)
    // android.jar only has stubs of org.json.
    testImplementation(libs.org.json)
}
