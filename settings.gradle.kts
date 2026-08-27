pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "tmt"

include("tmt-application")
include("tmt-bootstrap")
include("tmt-common")
include("tmt-input-http")
include("tmt-output-persistence:postgres")
include("tmt-output-storage:s3")
