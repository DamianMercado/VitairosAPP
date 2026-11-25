plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
}

android {
    namespace = "cl.vitairos.captura"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.vitairos.captura.mvp"
        minSdk = 26          // Android 8.0 — mínimo acordado con VITAIROS
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-mvp"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // URL base de la API simulada. Nunca apunta a infraestructura productiva de VITAIROS.
        buildConfigField("String", "API_BASE_URL", "\"https://api-mock.vitairos.local/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    testOptions {
        // Robolectric necesita los recursos y el manifest para crear la app en las pruebas unitarias.
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.maxHeapSize = "2g" }
    }
}

// Cobertura de pruebas unitarias (Kover). Se mide la LÓGICA de la app: reglas, datos, sincronización y
// ViewModels. Las pantallas Compose, el tema y el código generado quedan fuera de la medición.
kover {
    reports {
        filters {
            excludes {
                annotatedBy("androidx.compose.runtime.Composable", "androidx.compose.ui.tooling.preview.Preview")
                // Interfaz Compose (pantallas, componentes visuales, navegación y tema)
                packages(
                    "cl.vitairos.captura.ui.theme",
                    "cl.vitairos.captura.ui.components",
                    "cl.vitairos.captura.ui.navigation",
                )
                classes(
                    "*ScreenKt", "*ScreenKt\$*", "*ComposableSingletons*", "cl.vitairos.captura.MainActivity*",
                    // Código generado (BuildConfig, recursos R y DAOs de Room)
                    "*.BuildConfig", "*.R", "*.R\$*", "*_Impl", "*_Impl\$*",
                )
            }
        }
        verify {
            rule("Cobertura mínima de la lógica") {
                minBound(80)
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Persistencia local (offline-first)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Sincronización en segundo plano
    implementation(libs.androidx.work.runtime.ktx)

    // Red (API mock vía interceptor OkHttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)

    // Imágenes
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
}
