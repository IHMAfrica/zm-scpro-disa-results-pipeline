
plugins {
    id("java")
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Ensure consistent target bytecode when toolchains are not used
tasks.withType<JavaCompile> {
    options.release.set(17)
}

application {
    mainClass.set("zm.gov.moh.hie.scp.StreamingJob")
}

group = "zm.gov.moh.hie.scp"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

extra["flinkVersion"] = "1.20.2"
extra["log4jVersion"] = "2.25.1"

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("ca.uhn.hapi:hapi-structures-v25:2.5.1")


    // Flink APIs – include for local testing
    implementation("org.apache.flink:flink-streaming-java:${property("flinkVersion")}")
    implementation("org.apache.flink:flink-connector-base:${property("flinkVersion")}")
    implementation("org.apache.flink:flink-table-api-java-bridge:${property("flinkVersion")}")
    implementation("org.apache.flink:flink-json:${property("flinkVersion")}")

    // External connectors
    implementation("org.apache.flink:flink-connector-kafka:3.3.0-1.20")
    implementation("org.apache.flink:flink-connector-jdbc:3.3.0-1.20")

    // Jackson – include so TypeReference is available at runtime
    implementation("com.fasterxml.jackson.core:jackson-core:2.19.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.2")

    // PostgreSQL driver (needed at runtime)
    implementation("org.postgresql:postgresql:42.7.4")

    // Logging
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:${property("log4jVersion")}")
    implementation("org.apache.logging.log4j:log4j-api:${property("log4jVersion")}")
    implementation("org.apache.logging.log4j:log4j-core:${property("log4jVersion")}")

    // Needed for local development
    implementation("org.apache.flink:flink-clients:${property("flinkVersion")}")
    implementation("org.apache.flink:flink-java:${property("flinkVersion")}")
}

// Build a fat jar called *-all.jar that includes app necessary libs (not Flink APIs)
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveClassifier.set("all")
    archiveFileName.set("zm-scpro-disa-results-pipeline-all.jar")
    mergeServiceFiles()
    // Disable minimization for local testing to keep all Flink service providers
    // minimize {
    //     exclude(dependency("com.fasterxml.jackson.core:.*"))
    //     exclude(dependency("com.fasterxml.jackson.datatype:.*"))
    //     exclude(dependency("org.apache.flink:flink-connector-.*"))
    // }
    // Include Flink for local testing (comment out for Kubernetes deployment)
    // exclude("org/apache/flink/**")
}


tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaExec> {
    jvmArgs = listOf(
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.time=ALL-UNNAMED"
    )
}