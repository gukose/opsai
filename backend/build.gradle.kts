plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.hotelopai"
version = "0.0.1-SNAPSHOT"
description = "hotel-opai-backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Test>("verifyOpenApiContract") {
    description = "Verifies the runtime v1 OpenAPI contract against docs/api/openapi-v1.yaml."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.hotelopai.shared.api.OpenApiContractSnapshotTest")
    }
    systemProperty("hotelopai.openapi.contract.mode", "verify")
    outputs.upToDateWhen { false }
}

tasks.register<Test>("refreshOpenApiContract") {
    description = "Refreshes docs/api/openapi-v1.yaml from the runtime v1 OpenAPI contract."
    group = "documentation"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.hotelopai.shared.api.OpenApiContractSnapshotTest")
    }
    systemProperty("hotelopai.openapi.contract.mode", "refresh")
    outputs.upToDateWhen { false }
}

tasks.register<Test>("checkOpenApiCompatibility") {
    description = "Classifies docs/api/openapi-v1.yaml changes against the configured Git baseline."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.hotelopai.shared.api.compat.OpenApiCompatibilityTaskTest")
    }
    systemProperty("hotelopai.openapi.compatibility.task", "true")
    systemProperty("hotelopai.openapi.compatibility.mode", "check")
    outputs.upToDateWhen { false }
}

tasks.register<Test>("generateOpenApiChangelog") {
    description = "Regenerates docs/api/CHANGELOG.md from the OpenAPI compatibility comparison."
    group = "documentation"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.hotelopai.shared.api.compat.OpenApiCompatibilityTaskTest")
    }
    systemProperty("hotelopai.openapi.compatibility.task", "true")
    systemProperty("hotelopai.openapi.compatibility.mode", "changelog")
    outputs.upToDateWhen { false }
}

tasks.register<Test>("apaleoSandboxSmokeTest") {
    description = "Runs an opt-in read-only Apaleo sandbox smoke test when explicitly enabled."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.hotelopai.integration.apaleo.ApaleoSandboxSmokeTest")
    }
    systemProperty(
        "hotelopai.apaleo.sandbox.smoke.enabled",
        providers.gradleProperty("hotelopai.apaleo.sandbox.smoke.enabled").orElse("false").get()
    )
    outputs.upToDateWhen { false }
}

springBoot {
    mainClass.set("com.hotelopai.OpsaiApplicationKt")
}
