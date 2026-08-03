plugins {
    id("tmt-convention")
}

dependencies {
    implementation(project(":tmt-application"))
    implementation(libs.bundles.adaptor.input.http)
}
