plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
}


android {
    compileSdk = 35
    namespace = "com.tribalfs.milko"

    defaultConfig {
        applicationId = "com.tribalfs.milko"
        minSdk = 24

        versionCode = 1
        versionName = "0.0.1"

        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    implementation(libs.bundles.sesl.androidx)
    implementation(libs.sesl.material)
    implementation(libs.oneuiDesign)
    implementation(libs.oneuiIcons)

    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activityKtx)

    implementation(libs.bundles.androidx.datastore)
    implementation(libs.bundles.hilt)
    ksp(libs.bundles.hilt.compilers)
    implementation(libs.timber)
}

configurations.all{
    // Exclude official jetpack components lib
    exclude(group = "androidx.core", module = "core")
    exclude(group = "androidx.core", module = "core-ktx")
    exclude(group = "androidx.customview", module = "customview")
    exclude(group = "androidx.coordinatorlayout", module = "coordinatorlayout")
    exclude(group = "androidx.drawerlayout", module = "drawerlayout")
    exclude(group = "androidx.viewpager2", module = "viewpager2")
    exclude(group = "androidx.viewpager", module = "viewpager")
    exclude(group = "androidx.appcompat", module = "appcompat")
    exclude(group = "androidx.fragment", module = "fragment")
    exclude(group = "androidx.preference", module = "preference")
    exclude(group = "androidx.recyclerview", module = "recyclerview")
    exclude(group = "androidx.slidingpanelayout", module = "slidingpanelayout")
    exclude(group = "androidx.swiperefreshlayout", module = "swiperefreshlayout")
    exclude(group = "com.google.android.material", module = "material")
}
