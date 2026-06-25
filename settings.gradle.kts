pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // RootEncoder (com.github.pedroSG94) di-host di JitPack
        maven { url = uri("https://jitpack.io") }
        // Huawei Mobile Services (com.huawei.hms:location)
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
}

rootProject.name = "PERISAI TAB"
include(":app")
 