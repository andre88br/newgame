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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "newgame"

include(":core-game")

// O módulo :app só entra no build quando há um SDK do Android por perto.
//
// Sem isso, quem não tem o SDK instalado — um servidor de integração contínua rodando só
// os testes das regras, por exemplo — não consegue nem configurar o build: o Gradle tenta
// baixar o plugin do Android e falha antes de chegar a qualquer tarefa. Assim
// `./gradlew :core-game:test` funciona em qualquer lugar, e quem abre o projeto no Android
// Studio recebe o app normalmente.
//
// Para forçar um lado ou o outro: -Pnewgame.androidModule=true|false
val androidSdkFound = sequenceOf("ANDROID_HOME", "ANDROID_SDK_ROOT")
    .any { !System.getenv(it).isNullOrBlank() } ||
    File(rootDir, "local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

val includeAndroidModule = providers.gradleProperty("newgame.androidModule")
    .orNull?.toBooleanStrictOrNull() ?: androidSdkFound

// O build.gradle.kts da raiz precisa da mesma resposta para decidir se põe o AGP no
// classpath, e um script de projeto não consegue reavaliar isto de forma confiável tão
// cedo. Uma propriedade de sistema atravessa: o settings roda antes de qualquer projeto.
System.setProperty("newgame.androidModule", includeAndroidModule.toString())

if (includeAndroidModule) {
    include(":app")
} else {
    logger.lifecycle(
        "newgame: módulo :app fora do build — nenhum SDK do Android encontrado. " +
            "Defina ANDROID_HOME, ou deixe o Android Studio criar o local.properties, " +
            "ou force com -Pnewgame.androidModule=true.",
    )
}
