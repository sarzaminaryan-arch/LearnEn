import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

/**
 * Release signing is configured from, in order of precedence:
 *  1. Environment variables KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
 *  2. A `keystore.properties` file in the repository root with the keys
 *     storeFile, storePassword, keyAlias, keyPassword.
 *  3. Nothing at all - in that case no release signingConfig is attached and the
 *     release build output is unsigned. Debug builds never need any secret.
 *
 * No keystore, password or alias is ever committed to this repository.
 */
fun readSigningProperties(): Properties? {
    val fromEnv = listOf("KEYSTORE_PATH", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
        .all { !System.getenv(it).isNullOrBlank() }
    if (fromEnv) {
        return Properties().apply {
            setProperty("storeFile", System.getenv("KEYSTORE_PATH"))
            setProperty("storePassword", System.getenv("KEYSTORE_PASSWORD"))
            setProperty("keyAlias", System.getenv("KEY_ALIAS"))
            setProperty("keyPassword", System.getenv("KEY_PASSWORD"))
        }
    }
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) {
        return Properties().apply { propsFile.inputStream().use { load(it) } }
    }
    return null
}

val signingProperties: Properties? = run {
    val props = readSigningProperties() ?: return@run null
    val path = props.getProperty("storeFile")?.trim()
    if (path.isNullOrEmpty() || !file(path).exists()) null else props
}

android {
    namespace = "ir.laxipatem.lexiflash"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.laxipatem.lexiflash"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en", "fa")
    }

    signingConfigs {
        if (signingProperties != null) {
            create("release") {
                storeFile = rootProject.file(signingProperties.getProperty("storeFile").trim())
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
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
            if (signingProperties != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("offline") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_ONLINE", "false")
            // Offline build ships no network permission and no network library.
        }
        create("online") {
            dimension = "distribution"
            applicationIdSuffix = ".online"
            versionNameSuffix = "-online"
            buildConfigField("boolean", "IS_ONLINE", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Instrumented tests run against the normal, unminified debug APK. Keeping
    // release/R8 out of the test APK avoids minifying AndroidJUnitRunner itself.
    testBuildType = "debug"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable += setOf(
            // The app ships an English and a Persian catalogue; every other
            // locale correctly falls back to the English strings.
            "MissingTranslation",
            // minSdk is 24, so the legacy launcher fallback is intentional.
            "IconMissingDensityFolder",
            "MonochromeLauncherIcon"
        )
        baseline = file("lint-baseline.xml")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.documentfile)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Online flavour only: on-device machine translation (opt-in, user triggered).
    "onlineImplementation"(libs.mlkit.translate)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
