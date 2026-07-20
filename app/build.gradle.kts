plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "fr.easter.brewhome"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.easter.brewhome"
        minSdk = 26
        targetSdk = 34
        versionCode = 43
        versionName = "1.33"
    }

    // Clé de release BrewHome : identifiants dans ~/.gradle/gradle.properties
    // (BREWHOME_*), keystore dans ~/backup/Simon/brewhome-keys/. Sur une machine
    // sans la clé, repli silencieux sur la clé debug pour que le build passe.
    signingConfigs {
        val storePath = providers.gradleProperty("BREWHOME_STORE_FILE").orNull
        if (storePath != null && file(storePath).exists()) {
            create("release") {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("BREWHOME_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("BREWHOME_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("BREWHOME_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            // R8 : code et ressources minifiés (APK ~2× plus léger)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    // Les icônes étendues ne sont plus versionnées par le BOM (gelées en 1.7.x)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("io.coil-kt:coil-compose:2.6.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
