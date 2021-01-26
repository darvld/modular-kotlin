plugins {
    kotlin("multiplatform") version "1.4.20"
    kotlin("plugin.serialization") version "1.4.20"
    id("maven-publish")
}

group = "com.github.darvld"
version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    explicitApi()

    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        compilations.getByName("main").cinterops.create("external")
    }
    sourceSets {
        val nativeMain by getting {

            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")

            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.0.1")
            }
        }
        val nativeTest by getting
    }
}
