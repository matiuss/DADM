import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// La API key de Gemini se lee de local.properties (archivo que NO se sube a git):
//   GEMINI_API_KEY=tu_clave
//   GEMINI_MODEL=gemini-flash-lite-latest   (opcional; alias que Google mantiene al día)
// Si no se define aquí, la app permite ingresarla desde la pantalla de Ajustes.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val geminiApiKey: String = localProps.getProperty("GEMINI_API_KEY", "")
val geminiModel: String = localProps.getProperty("GEMINI_MODEL", "gemini-flash-lite-latest")

android {
    namespace = "co.edu.unal.reto11"
    compileSdk = 34

    defaultConfig {
        applicationId = "co.edu.unal.reto11"
        minSdk = 26 // LiteRT-LM requiere Android 8.0+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "GEMINI_MODEL", "\"$geminiModel\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Modelo de lenguaje ejecutado en el propio celular (sin internet ni API key).
    // https://github.com/google-ai-edge/LiteRT-LM — requiere compilador Kotlin 2.3+.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.1")

    // Igual que en el Reto10: sin librerías HTTP/JSON externas.
    // La API REST de Gemini se consume con HttpURLConnection y el JSON con org.json.

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
