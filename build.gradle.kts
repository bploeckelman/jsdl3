plugins {
}

subprojects {
    repositories {
        mavenCentral()
    }
}

tasks {
    val copylib by registering(Copy::class) {
        group = "sdl-native"
        description = "Copies SDL shared library to project root, the default working directory when running jsdl3-app"

        dependsOn(":sdl-native:libsdl")
        mustRunAfter(":sdl-native:libsdl")

        val sdlBuildDir = project(":sdl-native").layout.buildDirectory.dir("sdl")
        from(sdlBuildDir) {
            include(
                "Debug/SDL3.dll",
                "Release/SDL3.dll",
                "**/*.so",
                "**/*.dylib"
            )
        }

        // NOTE: this is where the library gets loaded from by default when running the test app
        val appWorkingDir = project(":jsdl3-app").layout.projectDirectory
        into(appWorkingDir)
    }
}
