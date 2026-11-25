import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.chessoverlay.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chessoverlay.ultimate"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        // Stockfish est compilé pour ARM64 (Galaxy S23 Ultra : Snapdragon 8 Gen 2).
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        // Clé de signature « debug » publique, versionnée pour que chaque APK construit par la CI
        // puisse mettre à jour le précédent sur le téléphone. Pour une diffusion publique (Play Store),
        // remplacez-la par votre propre clé via les variables d'environnement ci-dessous.
        create("shared") {
            val ks = System.getenv("CHESS_KEYSTORE_PATH")
            storeFile = if (ks != null) file(ks) else file("debug.keystore")
            storePassword = System.getenv("CHESS_KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("CHESS_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("CHESS_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
            applicationIdSuffix = ".debug"
        }
    }

    packaging {
        // Le binaire Stockfish doit être extrait sur le disque pour pouvoir être exécuté.
        jniLibs { useLegacyPackaging = true }
        resources { excludes += setOf("META-INF/*.kotlin_module", "META-INF/AL2.0", "META-INF/LGPL2.1") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

val stockfishBinary = file("src/main/jniLibs/arm64-v8a/libstockfish.so")
tasks.named("preBuild") {
    doFirst {
        if (!stockfishBinary.exists()) {
            throw GradleException(
                "Stockfish n'est pas compilé. Lancez d'abord : scripts/build_stockfish.sh android " +
                    "(la CI GitHub le fait automatiquement)."
            )
        }
    }
}

dependencies {
    implementation(project(":core:chess"))
    implementation(project(":core:engine"))
    implementation(project(":core:analysis"))
    implementation(project(":core:vision"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}

// Rendus d'écran (Paparazzi) alimentés par le vrai Stockfish hôte : ./gradlew :app:recordPaparazziDebug
tasks.withType<Test>().configureEach {
    systemProperty("stockfish.path", rootProject.file("build/stockfish-host/stockfish").absolutePath)
    systemProperty("fixtures.dir", rootProject.file("core/vision/src/test/resources").absolutePath)
}
