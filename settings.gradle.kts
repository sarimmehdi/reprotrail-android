pluginManagement {
    includeBuild("build-logic")
    includeBuild("tools/clean-android-skeleton-gradle-plugin/clean-android-skeleton-plugin")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "reprotrail-android"

include(":app")
include(":nav")
include(":ui")
include(":utils")
include(":runtime")
include(":runtime:data")
include(":runtime:domain")
