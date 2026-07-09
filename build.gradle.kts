plugins {
    application
    checkstyle
    pmd
}

group = "ir.sharif.pvz"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "ir.sharif.pvz.Main"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}

checkstyle {
    toolVersion = "10.17.0"
    configFile = file("checkstyle.xml")
    maxWarnings = 0
}

pmd {
    toolVersion = "7.13.0"
    ruleSetFiles = files("pmd-ruleset.xml")
    ruleSets = listOf()
    isConsoleOutput = true
}
