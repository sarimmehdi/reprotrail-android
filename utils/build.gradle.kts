import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionComposePluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "dev.reprotrail.utils"
}

libraryConfig {
    moduleType.set(ModuleType.UTILS)
}
