buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("org.postgresql:postgresql:42.7.3")
        classpath("org.flywaydb:flyway-database-postgresql:10.10.0")
    }
}

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    id("org.springframework.boot") version "3.3.6"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("plugin.jpa") version "2.0.21"
    id("org.flywaydb.flyway") version "10.10.0"
    jacoco
    id("org.hibernate.orm") version "6.6.0.Final"
}

group = "com.rorapps"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core:10.10.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.10.0")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.0.2")

    // PDF generation + metadata extraction
    implementation("org.apache.pdfbox:pdfbox:3.0.2")

    // EXIF / image metadata (Agent 3 — document forensics)
    implementation("com.drewnoakes:metadata-extractor:2.19.0")

    // Perceptual image hashing (Agent 3 — duplicate photo detection)
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // QR decoding (e-invoice originality check, Tier 1)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    // AWS S3
    implementation("software.amazon.awssdk:s3:2.25.28")

    // Logging
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // CSV ingestion (CPCB recycler directory)
    implementation("org.apache.commons:commons-csv:1.10.0")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    // Self-signed test certs for e-invoice IRP key tests (no JDK-internal APIs)
    testImplementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
    jvmToolchain(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

jacoco {
    toolVersion = "0.8.11"
}

flyway {
    url = System.getenv("DB_URL") ?: error("DB_URL env var not set")
    user = System.getenv("DB_USERNAME") ?: error("DB_USERNAME env var not set")
    password = System.getenv("DB_PASSWORD") ?: error("DB_PASSWORD env var not set")
    locations = arrayOf("filesystem:src/main/resources/db/migration")
    cleanDisabled = false
    driver = "org.postgresql.Driver"
    schemas = arrayOf("eprid", "public")
    defaultSchema = "eprid"
    baselineOnMigrate = true
    baselineVersion = "0"
}

hibernate {
    enhancement {}
}
