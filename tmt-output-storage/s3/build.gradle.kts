plugins {
    id("tmt-convention")
}

dependencies {
    implementation(project(":tmt-application"))
    implementation(libs.aws.s3)
}
