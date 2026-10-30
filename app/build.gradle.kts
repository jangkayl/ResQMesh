import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing secrets stay outside Git; without this file release builds are unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Optional developer-mode PIN (gitignored local.properties: resqmesh.devPin=...). Only a salted
// PBKDF2 hash reaches BuildConfig; the salt is a fixed non-secret so builds stay reproducible.
val devPinSalt = "resqmesh-dev-pin-v1"
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
// Must match DeveloperModePolicy.hash: PBKDF2WithHmacSHA1, 150,000 iterations, 160 bits, ASCII PIN.
fun devPinHash(pin: String): String {
    if (pin.isEmpty()) return ""
    val spec = PBEKeySpec(pin.toCharArray(), devPinSalt.toByteArray(Charsets.US_ASCII), 150_000, 160)
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        .joinToString("") { "%02x".format(it) }
}
// Same vector as DeveloperModePolicyTest, so the two derivations cannot drift.
check(devPinHash("known-PIN-4821") == "a9a37ddec1da4994002d80be9095b41ffaa08104") { "devPinHash drifted from DeveloperModePolicy" }
val configuredDevPin: String = localProperties.getProperty("resqmesh.devPin").orEmpty().trim()
if (configuredDevPin.any { it.code > 127 }) {
    throw GradleException("resqmesh.devPin in local.properties must be ASCII only")
}
if (configuredDevPin.isNotEmpty() && configuredDevPin.length < 8 &&
    gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
) {
    throw GradleException("resqmesh.devPin in local.properties must be at least 8 characters for release builds")
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "edu.cit.resqmesh"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "edu.cit.resqmesh"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
        buildConfigField("boolean", "BLE_L2CAP_ENABLED", "true")
        buildConfigField("String", "DEV_PIN_SALT", "\"$devPinSalt\"")
        buildConfigField("String", "DEV_PIN_HASH", "\"${devPinHash(configuredDevPin)}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // manage_devices.ps1 passes -PdevAbi to package only the connected phones' ABIs; plain builds stay universal.
        providers.gradleProperty("devAbi").orNull?.let { abis ->
            ndk { abiFilters += abis.split(",").map(String::trim).filter(String::isNotEmpty) }
        }
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Diagnostic builds can exercise native GATT fallback on modern phones.
            val enabled = providers.gradleProperty("bleL2cap").orElse("true").get().toBooleanStrict()
            buildConfigField("boolean", "BLE_L2CAP_ENABLED", enabled.toString())
            // Debug builds fall back to 0000 so developer mode works without setup.
            buildConfigField("String", "DEV_PIN_HASH", "\"${devPinHash(configuredDevPin.ifEmpty { "0000" })}\"")
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        // detekt covers test code; lint only needs app sources.
        ignoreTestSources = true
    }
}

// Always re-run unit tests so the Gradle build cache can't hide a flaky test.
tasks.withType<Test>().configureEach {
    outputs.cacheIf { false }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    testImplementation(libs.io.insert.koin.koin.test)
    testImplementation(libs.io.insert.koin.koin.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.play.services.location) // Fused Location for indoor accuracy
    implementation(libs.maplibre.android.sdk) // Local vector map rendering with native PMTiles support
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    implementation(libs.io.insert.koin.koin.android)
    implementation(libs.io.insert.koin.koin.androidx.compose)
}
