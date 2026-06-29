plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.oflix.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.oflix.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Rename APK output to include app name, build type and version
        setProperty("archivesBaseName", "Oflix-v1.1")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    // Enable C++ NDK Build
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // Coil for Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")
    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended")
    
    // Retrofit & OkHttp (for API)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Jetpack Media3 (ExoPlayer)
    val media3_version = "1.2.1"
    implementation("androidx.media3:media3-exoplayer:$media3_version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3_version")
    implementation("androidx.media3:media3-ui:$media3_version")
    implementation("androidx.media3:media3-session:$media3_version")
}

// Custom Gradle task to automatically copy the logo from the preloader directory
tasks.register<Copy>("copyPreloaderLogo") {
    val srcFile = file("../preloader/logo.png")
    val destDir = file("src/main/res/drawable")
    
    // Create destination folder if it doesn't exist
    destDir.mkdirs()
    
    from(srcFile)
    into(destDir)
    
    doFirst {
        println("Copying preloader/logo.png to app/src/main/res/drawable/logo.png...")
    }
}

// Make compilation depend on copyPreloaderLogo task
tasks.named("preBuild") {
    dependsOn("copyPreloaderLogo")
}
