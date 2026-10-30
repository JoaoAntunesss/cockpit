plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Google sign-in is only enabled if google-services.json exists (see GUIDE.md). Without it the app still builds.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.diariobordo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cockpit.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "2.1"
        // Chave pública do Logo.dev (opcional). Pões em gradle.properties: logoDevKey=pk_xxx
        buildConfigField("String", "LOGO_DEV_KEY", "\"${project.findProperty("logoDevKey") ?: ""}\"")
        // Chave do Open Charge Map (opcional, grátis em openchargemap.org). Pões em gradle.properties: ocmKey=xxxx
        buildConfigField("String", "OCM_KEY", "\"${project.findProperty("ocmKey") ?: ""}\"")
    }
    // Assinatura automática no GitHub Actions (ver .github/workflows/apk.yml). No Android Studio nada muda.
    val ciKs = System.getenv("CI_KEYSTORE")
    signingConfigs {
        if (ciKs != null) create("ci") {
            storeFile = file(ciKs)
            storePassword = System.getenv("CI_KS_PASS")
            keyAlias = System.getenv("CI_KEY_ALIAS")
            keyPassword = System.getenv("CI_KEY_PASS")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (ciKs != null) signingConfig = signingConfigs.getByName("ci")
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Login Google e cópia na nuvem
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")
}
