plugins {
    kotlin("jvm") version "1.8.0"
    application
}

group = "org.camilogo1200"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    //http://tempusfugitlibrary.org/
    //Java micro-library for writing & testing concurrent code
    //testImplementation("com.google.code.tempus-fugit:tempus-fugit:1.1")

    //jUnit
    testImplementation(platform("org.junit:junit-bom:5.9.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}


tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("MainKt")
}