plugins {
    id("tmt-convention")
}

dependencies {
    implementation(project(":tmt-application"))
    implementation(project(":tmt-input-http"))
    implementation(project(":tmt-output-persistence:postgres"))
    implementation(project(":tmt-output-llm"))
    implementation(project(":tmt-output-address"))
    implementation(project(":tmt-output-storage:s3"))

    implementation(libs.bundles.bootstrap)
}

tasks {
    bootJar {
        enabled = true
    }

    named("bootJar") {
        dependsOn("ktlintFormat")
    }

    getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
        mainClass.set("com.tmt.bootstrap.TmtBootstrapApplicationKt")
    }
}

springBoot {
    buildInfo()
}
