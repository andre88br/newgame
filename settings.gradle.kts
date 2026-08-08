pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        // google() entra na Fase 2, junto com o módulo :app (AGP e AndroidX).
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "newgame"

include(":core-game")
