plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// En GitHub Actions el número de ejecución sube solo; Android exige que cada
// versión instalada encima tenga un versionCode mayor que la anterior.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// La clave de firma real llega por variables de entorno (secretos de GitHub).
// Sin ellas (compilación local) se firma con la clave de debug.
val keystoreFile: String? = System.getenv("PIANO_KEYSTORE_FILE")

android {
    namespace = "com.federomano.pianoapp"
    // Compila contra Android 37 porque las librerías actuales lo piden;
    // la app sigue apuntando a Android 36 (targetSdk).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.federomano.pianoapp"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("piano") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("PIANO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PIANO_KEY_ALIAS")
                keyPassword = System.getenv("PIANO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystoreFile != null) "piano" else "debug")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
