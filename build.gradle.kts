plugins {
    application
    checkstyle
    pmd
}

application {
    mainClass = "com.github.aaronbittel.Moria"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

tasks.jar {
    manifest {
        "Main-Class" to application.mainClass
    }
}

tasks.test {
    useJUnitPlatform()

    val failedOnly = project.hasProperty("failedOnly")

    testLogging {

        if (!failedOnly) {
            events("passed", "failed", "skipped")
        } else {
            events("failed")
        }

        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

        showExceptions = true
        showCauses = true
        showStackTraces = true
        showStandardStreams = true
    }

}

checkstyle {
    toolVersion = "13.4.2"
    maxErrors = 0
    maxWarnings = 0
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:all")
    options.compilerArgs.add("-Werror")
}

pmd {
    toolVersion = "7.24.0"
    isConsoleOutput = true
}

tasks.named<Pmd>("pmdMain") {
    ruleSetFiles = files("config/pmd/pmdMain.xml")
    ruleSets = emptyList()
}

tasks.named<Pmd>("pmdTest") {
    ruleSetFiles = files("config/pmd/pmdTest.xml")
    ruleSets = emptyList()
}
