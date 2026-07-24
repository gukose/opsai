plugins {
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
    kotlin("plugin.jpa") version "2.3.21" apply false
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

// Root build script intentionally stays light.
// Feature modules live under backend/ and unimock/.

fun sdkNpmTask(
    name: String,
    script: String,
    descriptionText: String
) {
    tasks.register<Exec>(name) {
        description = descriptionText
        group = "verification"
        workingDir = layout.projectDirectory.dir("sdk/typescript").asFile
        commandLine("npm", "run", script)
    }
}

sdkNpmTask(
    name = "generateTypeScriptApiClient",
    script = "generate",
    descriptionText = "Regenerates the TypeScript SDK from docs/api/openapi-v1.yaml."
)

sdkNpmTask(
    name = "verifyTypeScriptApiClient",
    script = "verify",
    descriptionText = "Verifies the committed TypeScript SDK matches docs/api/openapi-v1.yaml."
)

sdkNpmTask(
    name = "buildTypeScriptApiClient",
    script = "build",
    descriptionText = "Type-checks the generated TypeScript SDK."
)

sdkNpmTask(
    name = "testTypeScriptApiClient",
    script = "test",
    descriptionText = "Runs TypeScript SDK consumer contract tests."
)

sdkNpmTask(
    name = "verifyTypeScriptApiClientVersion",
    script = "verify:version",
    descriptionText = "Verifies SDK semantic version, release metadata, and publication safeguards."
)

sdkNpmTask(
    name = "generateTypeScriptApiClientReleaseNotes",
    script = "generate:release-notes",
    descriptionText = "Regenerates deterministic TypeScript SDK release notes."
)

sdkNpmTask(
    name = "verifyTypeScriptApiClientReleaseNotes",
    script = "verify:release-notes",
    descriptionText = "Verifies TypeScript SDK release notes are current."
)

sdkNpmTask(
    name = "generateTypeScriptApiClientExportManifest",
    script = "generate:exports",
    descriptionText = "Regenerates the public TypeScript SDK export manifest."
)

sdkNpmTask(
    name = "verifyTypeScriptApiClientExportManifest",
    script = "verify:exports",
    descriptionText = "Verifies the public TypeScript SDK export manifest is current."
)

sdkNpmTask(
    name = "packTypeScriptApiClient",
    script = "pack:local",
    descriptionText = "Builds and packs the private TypeScript SDK into a local archive without publishing."
)

sdkNpmTask(
    name = "verifyPackedTypeScriptApiClient",
    script = "verify:packed",
    descriptionText = "Verifies packed TypeScript SDK contents and isolated consumer compilation."
)

sdkNpmTask(
    name = "verifyTypeScriptApiClientReleaseReadiness",
    script = "verify:release-readiness",
    descriptionText = "Runs all TypeScript SDK release-readiness checks without publishing."
)
