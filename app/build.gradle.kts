plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.kutudepom.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kutudepom.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.5.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val existingKey = System.getenv("KUTUDEPOM_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
    if (existingKey != null) {
        signingConfigs {
            create("existing") {
                storeFile = file(existingKey)
                storePassword = System.getenv("KUTUDEPOM_STORE_PASSWORD")
                keyAlias = System.getenv("KUTUDEPOM_KEY_ALIAS")
                keyPassword = System.getenv("KUTUDEPOM_KEY_PASSWORD")
            }
        }
        buildTypes.getByName("debug").signingConfig = signingConfigs.getByName("existing")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
}

