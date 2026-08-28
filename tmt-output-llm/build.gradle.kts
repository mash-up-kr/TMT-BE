plugins {
    id("tmt-convention")
}

dependencies {
    implementation(project(":tmt-application"))
    // RestClient — 서버 스택 없이 HTTP 클라이언트만 쓴다
    implementation(libs.spring.web)
}
