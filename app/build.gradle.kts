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
        versionCode = 8
        versionName = "1.2.0"
        // Parâmetros do "Acesso pela rede", sem mexer no código (sem segredos: só portas e tempos).
        // Ex.: ./gradlew assembleRelease -PfinanLan="httpsPort=9443;idleMinutes=5"
        // ou a variável de ambiente ORG_GRADLE_PROJECT_finanLan. Vazio (F-Droid, Releases) = padrões de LanConfig.
        val lanConfig = (project.findProperty("finanLan") as String?).orEmpty()
        require(Regex("^[A-Za-z0-9=;]*$").matches(lanConfig)) { "finanLan: use só chave=valor;chave=valor" }
        buildConfigField("String", "LAN_CONFIG", "\"$lanConfig\"")
    }

    // Dois canais de publicação:
    // - F-Droid: compila este código e assina o APK com a chave dele (as variáveis abaixo não existem lá).
    // - GitHub Releases: o GitHub Actions assina com a chave do autor, guardada nos Secrets do repositório.
    // A chave (.jks) nunca entra no código. Sem as variáveis, o APK de release sai sem assinatura.
    val keystoreFile = System.getenv("FINAN_KEYSTORE_FILE")
    if (keystoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("FINAN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FINAN_KEY_ALIAS")
                keyPassword = System.getenv("FINAN_KEY_PASSWORD")
            }
        }
    }

    // Sem o bloco de dependências cifrado pelo Google dentro do APK (o F-Droid recusa esse bloco).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("release")
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
    // Capturas de tela da documentação (docs/screenshots): `./gradlew testDebugUnitTest -PfinanScreenshots=true`.
    // Sem a propriedade, os testes de captura ficam de fora (o build normal não baixa nem roda o Robolectric).
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                val shots = project.findProperty("finanScreenshots") == "true"
                if (shots) {
                    test.systemProperty("roborazzi.test.record", "true")
                    test.systemProperty("finan.screenshots.dir", rootProject.file("docs/screenshots").absolutePath)
                    test.filter.includeTestsMatching("com.finanplus.screenshots.*")
                } else {
                    test.exclude("**/screenshots/**")
                }
            }
        }
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
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.kotlin.test.junit)
}
