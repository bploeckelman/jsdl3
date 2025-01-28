plugins {
    id("buildlogic.java-application-conventions")
}

dependencies {
    implementation(project(":sdl-bindings"))
}

application {
    mainClass = "net.bplo.jsdl3.App"
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED"
    )
}
