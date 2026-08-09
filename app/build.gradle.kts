import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Sem versão: o AGP vem do classpath do buildscript da raiz, para ficar no mesmo
    // classloader do plugin Kotlin. Veja o comentário no build.gradle.kts da raiz.
    id("com.android.application")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // SavedMatch é @Serializable: sem este plugin aqui, o módulo nem compila.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.andre88br.newgame.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.andre88br.newgame"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // O plugin Kotlin já costuma registrar src/main/kotlin, mas declarar não custa nada e
    // evita o pior modo de falha possível: um build que passa e gera um APK sem código.
    sourceSets["main"].java.srcDirs("src/main/kotlin")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Trava de teto para o AndroidX.
//
// Declarar a versão em cada dependência não basta: se qualquer transitiva pedir uma mais
// nova, o Gradle sobe para ela — e a partir de core 1.18 / lifecycle 2.10 o AndroidX exige
// AGP 9.1+, que o plugin Kotlin ainda não acompanha. O build então falha com uma mensagem
// que não aponta para o culpado. Fixar aqui torna o teto explícito e o erro impossível.
//
// Assim que o kotlin-android suportar o AGP 9, some daqui e sobem as versões no catálogo.
configurations.configureEach {
    resolutionStrategy {
        force(
            "androidx.core:core:${libs.versions.androidxCore.get()}",
            "androidx.core:core-ktx:${libs.versions.androidxCore.get()}",
            "androidx.lifecycle:lifecycle-runtime:${libs.versions.lifecycle.get()}",
            "androidx.lifecycle:lifecycle-runtime-compose:${libs.versions.lifecycle.get()}",
            "androidx.lifecycle:lifecycle-viewmodel:${libs.versions.lifecycle.get()}",
            "androidx.lifecycle:lifecycle-viewmodel-compose:${libs.versions.lifecycle.get()}",
        )
    }
}

dependencies {
    // Todas as regras de jogo, a IA e a orquestração da partida vêm daqui. Este módulo
    // só desenha e liga os fios.
    implementation(project(":core-game"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
