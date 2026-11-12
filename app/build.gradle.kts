import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "com.youriptv.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.youriptv.app"
        minSdk = 23
        targetSdk = 36
        // 1.0.5: protected playlist-management visit, visible deletion and password visibility.
        versionCode = (project.findProperty("updateTestVersionCode") as String?)?.toInt() ?: 36
        versionName = (project.findProperty("updateTestVersionName") as String?) ?: "1.0.5"
        val updateTestOrigin = (project.findProperty("updateTestOrigin") as String?).orEmpty()
        val updateTestBackupOrigin = (project.findProperty("updateTestBackupOrigin") as String?).orEmpty()
        val updateManifest = if (updateTestOrigin.isEmpty()) {
            "https://github.com/youriptvus/YourIPTV/releases/latest/download/update.json"
        } else {
            "${updateTestOrigin}update.json"
        }
        val backupManifest = when {
            updateTestBackupOrigin.isNotEmpty() -> "${updateTestBackupOrigin}update.json"
            updateTestOrigin.isNotEmpty() -> ""
            else -> "https://youriptv.us/updates/update.json"
        }
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifest\"")
        buildConfigField("String", "UPDATE_BACKUP_MANIFEST_URL", "\"$backupManifest\"")
        buildConfigField("String", "UPDATE_TEST_ORIGIN", "\"$updateTestOrigin\"")
        buildConfigField("String", "UPDATE_TEST_BACKUP_ORIGIN", "\"$updateTestBackupOrigin\"")
    }

    // Release signing is read from the untracked keystore.properties at the project root.
    val keystoreFile = rootProject.file("keystore.properties")
    val releaseSigning = if (keystoreFile.exists()) {
        val props = Properties().apply { keystoreFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else null

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        viewBinding = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val media3 = "1.11.1"
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("net.sf.kxml:kxml2:2.2.2")
}
