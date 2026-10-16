import java.util.Properties
import java.io.FileInputStream
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.baskom.pembacakue"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.baskom.pembacakue"
        minSdk = 24
        targetSdk = 36
        versionCode = (project.findProperty("VERSION_CODE") as? String)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("VERSION_NAME") as? String) ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Open-source default: optional IronSource App Key (configured via local.properties or gradle -P)
        buildConfigField("String", "IRONSOURCE_APP_KEY", "\"${project.findProperty("IRONSOURCE_APP_KEY") ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localProperties.load(FileInputStream(localPropertiesFile))
            }
            val storeFilePath = localProperties.getProperty("RELEASE_STORE_FILE")
            if (storeFilePath != null && file(storeFilePath).exists()) {
                storeFile = file(storeFilePath)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseConfig = signingConfigs.getByName("release")
            signingConfig = if (releaseConfig.storeFile != null && releaseConfig.storeFile!!.exists()) {
                releaseConfig
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
        debug {
            // Crash saat pengembangan tidak perlu ikut ke dasbor produksi.
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Skema Room diekspor supaya migrasi bisa diuji terhadap skema versi sebelumnya,
    // bukan hanya diasumsikan benar. exportSchema sebelumnya false sehingga tidak ada
    // acuan apa pun untuk memverifikasi MIGRATION_1_2 dan migrasi berikutnya.
    // android.util.Log dkk mengembalikan nilai default, bukan melempar exception.
    // Tanpa ini parser yang menulis log gagal di unit test — dan kegagalannya
    // tersamar karena parser menangkap Exception lalu mengembalikan null.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    sourceSets.getByName("androidTest") {
        assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Tanpa versi eksplisit: ikon berasal dari Compose BOM yang sama dengan artefak
    // Compose lainnya. Versi yang dipatok manual (1.6.0) tidak sejalan dengan BOM dan
    // menarik compose-ui versi lama ke dalam graf dependensi.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.airbnb.android:lottie-compose:6.4.0")
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    // Crashlytics menggantikan pengumpulan crash manual lewat berkas log di repo.
    implementation(libs.firebase.crashlytics)
    
    // Room Database
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
    testImplementation("androidx.room:room-testing:$room_version")
    // MigrationTestHelper berjalan di perangkat/emulator, jadi dependensinya harus
    // ada di androidTest — bukan hanya di unit test.
    androidTestImplementation("androidx.room:room-testing:$room_version")
    
    testImplementation(libs.junit)
    // org.json di android.jar hanya stub; tanpa implementasi nyata, tes parsing JSON
    // diam-diam "lulus" dengan daftar kosong.
    testImplementation("org.json:json:20250107")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:8.0.0")
    
    // Unity Ads (LevelPlay)
    implementation("com.ironsource.sdk:mediationsdk:8.2.0")
    implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")

    // Google User Messaging Platform — CMP bersertifikat untuk consent iklan (GDPR/EEA)
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")

    // Location Services for Toll Calculator
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Google Play In-App Review (SDK Resmi)
    implementation("com.google.android.play:review-ktx:2.0.2")

    // Google Play In-App Updates — pengingat versi terbaru (opsional, mode flexible)
    implementation("com.google.android.play:app-update-ktx:2.1.0")
}