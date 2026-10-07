plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.finanplus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.finanplus"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.1.1"
    }

    // Assinatura da versão de publicação: só pelo GitHub Actions, com a chave guardada nos Secrets do repositório
    // (variáveis abaixo). A chave (.jks) nunca entra no código. Sem as variáveis, o build local funciona como sempre
    // e a assinatura é feita pelo Android Studio (Build › Generate Signed App Bundle / APK).
    val ciKeystore = System.getenv("FINAN_KEYSTORE_FILE")
    if (ciKeystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("FINAN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FINAN_KEY_ALIAS")
                keyPassword = System.getenv("FINAN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (ciKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
