plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.kotlinSerializationPlugin)
}

android {
    namespace = "dev.reprotrail.runtime"
}

dependencies {
    implementation(libs.kotlinxSerializationJsonLibrary)
    implementation(libs.koinCoreLibrary)
    testImplementation(libs.koinTestLibrary)
    testImplementation(libs.roboelectricLibrary)
}
