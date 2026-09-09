plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace="com.bnet.app"; compileSdk=35
    defaultConfig { applicationId="com.bnet.app"; minSdk=26; targetSdk=35; versionCode=4; versionName="0.4.0" }
    buildFeatures { compose=true }
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
    implementation("androidx.compose.animation:animation")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
}
