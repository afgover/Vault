plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.afgover.vault"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.afgover.vault"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.biometric)
    // biometric 1.1.0 geçişli olarak fragment 1.2.5'i getirir. O sürümdeki
    // FragmentActivity.startActivityForResult, requestCode'un yalnızca alt 16 biti
    // kullanmasını şart koşar; ActivityResultRegistry ise her zaman 0x10000'den büyük
    // kod üretir. Sonuç: rememberLauncherForActivityResult ile açılan her ekran
    // (yedek al / geri yükle / otomatik doldurma ayarı / Bluetooth izni) çöker.
    // Bu yüzden fragment sürümü açıkça yükseltiliyor — kaldırma.
    // (MainActivity zaten FragmentActivity'den türediği için doğrudan bağımlılık doğru.)
    implementation(libs.androidx.fragment)

    // QR ile doğrudan aktarım: kamera + çevrimdışı çözücü.
    // ML Kit bilinçli olarak kullanılmadı (Play Services bağımlılığı, SEC-019).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    // JVM birim testleri: BackupManager java.util.Base64 kullanır (minSdk 26),
    // org.json ise Android'in çalışma zamanı sınıflarıyla aynı pakettir.
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)

    constraints {
        // Doğrudan bağımlılık ileride kaldırılsa bile hiçbir geçişli bağımlılık
        // fragment'i 1.7.1'in altına düşüremesin.
        implementation(libs.androidx.fragment)

    // QR ile doğrudan aktarım: kamera + çevrimdışı çözücü.
    // ML Kit bilinçli olarak kullanılmadı (Play Services bağımlılığı, SEC-019).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    // JVM birim testleri: BackupManager java.util.Base64 kullanır (minSdk 26),
    // org.json ise Android'in çalışma zamanı sınıflarıyla aynı pakettir.
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner) {
            because(
                "fragment 1.7.1'den eskisi ActivityResultRegistry'nin ürettiği " +
                    "requestCode'ları reddediyor (Can only use lower 16 bits for requestCode)"
            )
        }
    }
}

/**
 * 16 KB sayfa uyumu: `assembleDebug` sonrası yerel kütüphanelerin ELF
 * hizalaması ölçülür. 4 KB hizalı bir `.so` cihazda "uygulama 16 KB ile
 * uyumlu değil" uyarısı verir ve derlemede hiçbir iz bırakmaz; bu kontrol
 * onu derleme zamanına taşır (CameraX 1.3.4 ile tam bunu yaşadık).
 */
tasks.register<Exec>("checkApkAlignment") {
    dependsOn("assembleDebug")
    commandLine(
        "python3",
        "${rootDir}/tools/apk-hizalama.py",
        "${layout.buildDirectory.get()}/outputs/apk/debug/app-debug.apk"
    )
}
