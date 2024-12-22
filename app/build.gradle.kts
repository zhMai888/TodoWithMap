plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt") // 添加 kapt 插件
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    dexOptions {
        dexInProcess = true
        preDexLibraries = true
        javaMaxHeapSize = "6g"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding=true
    }
    kapt {
        arguments {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 添加 Room 数据库依赖
    implementation("androidx.room:room-runtime:2.5.0")
    kapt("androidx.room:room-compiler:2.5.0")

    // 可选：支持协程的 Room 扩展
    implementation("androidx.room:room-ktx:2.5.0")

    // 协程支持
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 添加地图、地理位置依赖
    implementation(libs.play.services.location)
    implementation("com.baidu.lbsyun:BaiduMapSDK_Map:7.5.0")
    implementation("com.baidu.lbsyun:BaiduMapSDK_Location:9.6.0")

    // 转成Json的gson库
    implementation(libs.gson)

    // 支持生命周期协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.0")
    // 支持协程
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.4.1")
}
