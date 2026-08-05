plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.kotlinSerializationPlugin)
}

android {
    namespace = "dev.reprotrail.runtime"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(libs.kotlinxSerializationJsonLibrary)
    implementation(libs.koinCoreLibrary)
    testImplementation(libs.koinTestLibrary)
    testImplementation(libs.roboelectricLibrary)
}
