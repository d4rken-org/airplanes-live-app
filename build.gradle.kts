plugins {
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.3.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.4.10")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.60.1")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.10")
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
