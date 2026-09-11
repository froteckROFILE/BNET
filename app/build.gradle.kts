plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace="com.bnet.app"; compileSdk=35
    defaultConfig {
        applicationId="com.bnet.guard"; minSdk=26; targetSdk=35; versionCode=3; versionName="0.3.0"
        buildConfigField("String", "SUPABASE_URL", "\"https://lqsplflluosfbinszoqn.supabase.co\"")
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_Gk-jPZm1hSqCQh_1ChRZog_098HlOlX\"")
    }
    buildFeatures { compose=true; buildConfig=true }
    composeOptions { kotlinCompilerExtensionVersion="1.5.14" }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget="17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:face-detection:16.1.7")
}
