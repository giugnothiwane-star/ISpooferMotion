import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core:chess"))
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    // Binaire Stockfish hôte (scripts/build_stockfish.sh host) pour les tests d'intégration.
    systemProperty("stockfish.path", rootProject.file("build/stockfish-host/stockfish").absolutePath)
    systemProperty("fixtures.dir", rootProject.file("core/vision/src/test/resources").absolutePath)
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
