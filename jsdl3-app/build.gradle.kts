plugins {
    id("buildlogic.java-application-conventions")
}

dependencies {
    implementation("org.apache.commons:commons-text")
    implementation(project(":sdl-bindings"))
}

application {
    mainClass = "net.bplo.jsdl3.App"
}
