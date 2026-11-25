pluginManagement {
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
rootProject.name = "ChessOverlayAI"
include(":app")
include(":core:chess")
include(":core:engine")
include(":core:analysis")
include(":core:vision")
include(":core:data")
include(":core:ui")
