plugins {
    application
    checkstyle
    pmd
    id("org.openjfx.javafxplugin") version "0.1.0"
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

// JavaFX 21 is the last release that still compiles against a Java 17 target,
// which keeps the phase-1 toolchain settings below untouched.
javafx {
    version = "21.0.5"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.media", "javafx.swing")
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

/**
 * The phase-1 build: the same game driven by typed commands rather than the
 * JavaFX window. "run" opens the window; this one stays in the terminal.
 */
tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "Plays the game in the terminal, the way phase 1 is graded."
    mainClass = "ir.sharif.pvz.Main"
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("--cli")
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}

/**
 * Renders every screen of the graphical view to build/snapshots for review.
 * A development aid, not part of the build.
 */
tasks.register<JavaExec>("snapshots") {
    group = "verification"
    description = "Writes a PNG of each screen to build/snapshots."
    mainClass = "ir.sharif.pvz.devtools.SnapshotLauncher"
    classpath = sourceSets["test"].runtimeClasspath
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
