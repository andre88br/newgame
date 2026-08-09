// O AGP entra pelo classpath do buildscript, e não pelo bloco `plugins`. Não é preferência
// de estilo: é o que faz o projeto compilar.
//
// O plugin kotlin-android precisa enxergar as classes do AGP (BaseExtension, BaseVariant)
// no mesmo classloader. Declarar o AGP só dentro do :app o deixa num classloader separado
// do Kotlin, que vem da raiz, e o build morre com NoClassDefFoundError em
// com.android.build.gradle.BaseExtension — sempre, em qualquer versão do AGP.
//
// O jeito comum de resolver é declarar os dois na raiz com `apply false`. Só que
// `plugins { alias(agp) }` é resolvido em toda configuração do build, inclusive em máquina
// sem acesso ao Google Maven, e isso derrubaria `:core-game:test` — que precisa continuar
// rodando em qualquer lugar. O bloco `buildscript` aceita `if`; o bloco `plugins`, não.
buildscript {
    // A bandeira é calculada no settings.gradle.kts, que roda antes deste arquivo.
    if (System.getProperty("newgame.androidModule") == "true") {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            // Versão do AGP. Fica aqui, e não no catálogo, porque o catálogo não é visível
            // dentro de um bloco buildscript.
            classpath("com.android.tools.build:gradle:8.13.2")
        }
    }
}

// Os plugins do Kotlin ficam declarados aqui, com `apply false`, para que o build inteiro
// use uma versão só: o :core-game aplica o kotlin.jvm e o :app aplica o kotlin.android, e
// os dois saem do mesmo artefato. Todos vêm do Maven Central, então não atrapalham quem
// constrói sem SDK do Android.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
