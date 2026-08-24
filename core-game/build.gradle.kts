import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Módulo Kotlin/JVM puro: nenhuma dependência de Android, para que as regras dos
// jogos possam ser testadas sem o SDK e reaproveitadas em qualquer front-end.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
    }
    // Campanhas de afinação da IA são longas demais para a suíte de sempre, então rodam só
    // quando pedidas: `-Pnewgame.campanha=rodadas,sementes`. Sem isso, a bancada roda curta.
    systemProperty("newgame.campanha", (project.findProperty("newgame.campanha") ?: "").toString())
}
