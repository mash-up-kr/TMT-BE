plugins {
    id("tmt-convention")
}

dependencies {
    implementation(project(":tmt-application"))
    implementation(libs.bundles.adaptor.input.http)
    // JWT 발급·검증 (TMT-272). impl·jackson은 런타임에만 필요하다
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
}
