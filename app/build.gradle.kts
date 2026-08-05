plugins {
    alias(libs.plugins.conventionApplicationPluginId)
    alias(libs.plugins.androidApplicationPlugin)
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":runtime"))
    testImplementation(libs.roboelectricLibrary)
}
