import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "dev.reprotrail.nav"
}

libraryConfig {
    moduleType.set(ModuleType.NAV)
}
