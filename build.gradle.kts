// Os plugins do Android não são declarados aqui de propósito.
//
// Mesmo com `apply false`, declarar um plugin na raiz faz o Gradle resolvê-lo já na
// configuração do build — e isso derrubaria `:core-game:test` em qualquer máquina sem
// acesso ao Google Maven, que é justamente onde as regras dos jogos precisam continuar
// testáveis. Cada módulo declara o que usa, com a versão vindo do catálogo.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
