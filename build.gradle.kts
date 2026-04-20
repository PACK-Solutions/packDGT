plugins {
    kotlin("jvm") version "2.1.0"
    application
}

group = "com.example"
version = "1.0.0"

application {
    mainClass.set("com.example.packdgt.ApplicationKt")
}

val ktorVersion = "3.0.3"
val poiVersion = "5.3.0"
val pdfboxVersion = "3.0.3"
val logbackVersion = "1.5.11"
val jacksonVersion = "2.18.1"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-config-yaml:$ktorVersion")

    // Jackson Kotlin module
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // Apache POI - DOCX template manipulation
    implementation("org.apache.poi:poi-ooxml:$poiVersion") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
    }

    // DOCX → PDF : JODConverter (pool de LibreOffice résidents, conversion par socket)
    implementation("org.jodconverter:jodconverter-local:4.4.7")

    // Apache PDFBox 3 - PDF post-processing (watermark, métadonnées, protection)
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")

    // Logging — route log4j-api through SLF4J/Logback
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.apache.logging.log4j:log4j-to-slf4j:2.24.1")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
