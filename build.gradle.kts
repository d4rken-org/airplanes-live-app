plugins {
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.2.0")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.57.2")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.2.0-2.0.2")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register("clean").configure {
    delete("build")
}
