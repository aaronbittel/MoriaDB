plugins {
    application
    checkstyle
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

    testLogging {
        events("passed", "failed", "skipped")

        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

        showExceptions = true
        showCauses = true
        showStackTraces = true
    }

}

checkstyle {
    toolVersion = "13.4.2"
    maxErrors = 0
    maxWarnings = 0
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint")
    options.compilerArgs.add("-Werror")
}
