plugins {
    id("tmt-convention")
}

dependencies {
    implementation(project(":tmt-application"))
    implementation(libs.bundles.adaptor.persistence.mysql)

    // QueryDSL (Jakarta)
    implementation(variantOf(libs.querydsl.jpa) { classifier("jakarta") })
    kapt(variantOf(libs.querydsl.apt) { classifier("jakarta") })
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
