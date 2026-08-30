import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}
val debugHost = localProperties.getProperty("backend.host", "10.0.2.2")
val releaseApiUrl = localProperties.getProperty(
    "backend.release.apiUrl",
    localProperties.getProperty("underthemask.release.apiUrl", "https://mask.madebykole.dev/api/"),
)
val releaseWsUrl = localProperties.getProperty(
    "backend.release.wsUrl",
    localProperties.getProperty("underthemask.release.wsUrl", "wss://mask.madebykole.dev/ws"),
)
val signingStoreFilePath = localProperties.getProperty("underthemask.signing.storeFile")
val signingStorePassword = localProperties.getProperty("underthemask.signing.storePassword")
val signingKeyAlias = localProperties.getProperty("underthemask.signing.keyAlias")
val signingKeyPassword = localProperties.getProperty("underthemask.signing.keyPassword")
val releaseSigningConfigured = listOf(
    signingStoreFilePath,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword,
).all { !it.isNullOrBlank() }

gradle.taskGraph.whenReady {
    val releaseBuildRequested = allTasks.any { it.name == "assembleRelease" || it.name == "bundleRelease" }
    if (releaseBuildRequested) {
        val missingSigningProperties = listOf(
            "underthemask.signing.storeFile" to signingStoreFilePath,
            "underthemask.signing.storePassword" to signingStorePassword,
            "underthemask.signing.keyAlias" to signingKeyAlias,
            "underthemask.signing.keyPassword" to signingKeyPassword,
        ).filter { (_, value) -> value.isNullOrBlank() }

        if (missingSigningProperties.isNotEmpty()) {
            throw GradleException(
                "Release signing is not configured. Add these values to android/local.properties: " +
                    missingSigningProperties.joinToString { it.first },
            )
        }

        val signingStoreFile = file(signingStoreFilePath!!)
        if (!signingStoreFile.isFile) {
            throw GradleException(
                "Release signing keystore does not exist: ${signingStoreFile.absolutePath}",
            )
        }
    }
}

android {
    namespace = "com.underthemask.android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.underthemask.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(signingStoreFilePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://$debugHost:8080/api/\"")
            buildConfigField("String", "WS_URL", "\"ws://$debugHost:8080/ws\"")
            buildConfigField("String", "BACKEND_HOST", "\"$debugHost\"")
            buildConfigField("boolean", "REQUIRES_LOCAL_NETWORK_PERMISSION", "true")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiUrl\"")
            buildConfigField("String", "WS_URL", "\"$releaseWsUrl\"")
            buildConfigField("String", "BACKEND_HOST", "\"production\"")
            buildConfigField("boolean", "REQUIRES_LOCAL_NETWORK_PERMISSION", "false")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.krossbow.stomp.core)
    implementation(libs.krossbow.websocket.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
