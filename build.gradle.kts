// Os plugins do Kotlin são declarados aqui, com `apply false`, porque o Gradle precisa de
// **uma** versão conhecida deles para o build inteiro: o :core-game aplica o kotlin.jvm e o
// :app aplica o kotlin.android, e os dois saem do mesmo artefato. Sem esta declaração, o
// segundo módulo a pedir esbarra em "the plugin is already on the classpath with an unknown
// version". Todos vêm do Maven Central, então declará-los não atrapalha quem não tem SDK.
//
// Já o plugin do Android fica de fora de propósito, declarado só no :app. Mesmo com
// `apply false`, declarar aqui faria o Gradle resolvê-lo na configuração do build — e isso
// derrubaria `:core-game:test` em qualquer máquina sem acesso ao Google Maven, que é
// justamente onde as regras dos jogos precisam continuar testáveis.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
