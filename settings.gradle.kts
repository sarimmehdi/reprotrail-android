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
        exclusiveContent {
            forRepository {
                maven {
                    name = "KoinEmbedded"
                    url = uri("https://repository.kotzilla.io/repository/Koin-Embedded/")
                }
            }
            filter {
                includeModule("io.insert-koin", "embedded-koin-core")
                includeModule("io.insert-koin", "embedded-koin-core-jvm")
            }
        }
    }
}

rootProject.name = "reprotrail-android"

include(":app")
include(":nav")
include(":ui")
include(":utils")
include(":runtime")
include(":runtime-compose")
include(":runtime:data")
include(":runtime:domain")
include(":runtime:upload:data")
include(":runtime:upload:domain")
