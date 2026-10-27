plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ejemplo.bloqueador"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ejemplo.bloqueador"
        // Android 10+: necesario para el rol de "filtrado de llamadas" (RoleManager)
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "1.4"
    }

    // Llave fija para que cada versión nueva se instale encima de la anterior.
    // (Solo para uso personal; para publicar en Play Store usa una llave privada.)
    signingConfigs {
        create("fija") {
            storeFile = file("bloqueador.keystore")
            storePassword = "bloqueador"
            keyAlias = "bloqueador"
            keyPassword = "bloqueador"
            // Las tres firmas, para que lo acepten todos los instaladores (Oppo, Realme, Xiaomi…)
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fija")
        }
        release {
            // Quita el código e íconos que no se usan (el paquete de íconos completo es grande)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("fija")
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
    val composeBom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
