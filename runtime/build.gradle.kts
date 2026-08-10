import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.kotlinSerializationPlugin)
}

android {
    namespace = "dev.reprotrail.runtime"
    testOptions.unitTests.isIncludeAndroidResources = true
}

libraryConfig {
    moduleType.set(ModuleType.SDK)
}

dependencies {
    implementation(project(":runtime:data"))
    implementation(project(":runtime:domain"))
    implementation(libs.bundles.roomBundle)
    implementation(libs.kotlinxCoroutinesCoreLibrary)
    implementation(libs.kotlinxSerializationJsonLibrary)
    implementation(libs.koinCoreLibrary)
    testImplementation(libs.kotlinxCoroutinesTestLibrary)
    testImplementation(libs.koinTestLibrary)
    testImplementation(libs.roboelectricLibrary)
}
