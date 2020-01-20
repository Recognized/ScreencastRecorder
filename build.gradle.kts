buildscript {
    repositories {
        mavenCentral()
        jcenter()
    }
}

repositories {
    maven("https://jitpack.io")
}

plugins {
    kotlin("jvm") version "1.3.61"
    id("org.jetbrains.intellij") version "0.3.9"
    id("org.jetbrains.grammarkit") version "2018.1.7"
}

apply {
    plugin("idea")
}

val kotlinVersion = "1.3.61"
val compileKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
val compileTestKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks

compileKotlin.kotlinOptions.verbose = true
compileKotlin.kotlinOptions.jvmTarget = "1.8"
compileTestKotlin.kotlinOptions.jvmTarget = "1.8"


val idea = plugins.findPlugin(IdeaPlugin::class)!!

idea.model.module.isDownloadJavadoc = true
idea.model.module.isDownloadSources = true

intellij {
    pluginName = "ScreencastRecorder"
    setPlugins("com.intellij.testGuiFramework:183.6156.11")
    version = "183.3647.12"
    downloadSources = true
    instrumentCode = true
    
}

//val jar: org.gradle.jvm.tasks.Jar by tasks
//jar.from(sourceSets.main)
//jar.configure<org.gradle.jvm.tasks.Jar> {
//    from(sourceSets.main.)
//    from(sourceSets.main.output)
//}
//
val patchPluginXml: org.jetbrains.intellij.tasks.PatchPluginXmlTask by tasks
patchPluginXml.apply {
    sinceBuild("182")
    version("1.0")
}

val publishPlugin: org.jetbrains.intellij.tasks.PublishTask by tasks
publishPlugin.enabled = true

repositories {
    mavenCentral()
}

dependencies {
    compile("javazoom:jlayer:1.0.1")
    compile("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    compile("com.github.Recognized:kotlin-ranges-union:v1.01") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    compile("com.github.Recognized:kotlin-ranges-extensions:v1.01") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    compile("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.0")
    testCompile("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
}