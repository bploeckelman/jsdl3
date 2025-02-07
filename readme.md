# jsdl3

## What is this?

An experiment for learning about the [Java 22+ Foreign Function and Memory API (FFM)](https://docs.oracle.com/en/java/javase/22/core/foreign-function-and-memory-api.html)
by exposing [SDL3 `v3.2.0+`](https://github.com/libsdl-org/SDL/tree/release-3.2.0) as a Java library.

## How does it work?

I'll let you know once there's an 'it' that works.

## Project Notes

### Setup

Clone this repo with `git clone --recurse-submodules https://github.com/bploeckelman/jsdl3.git` to
automatically pull the SDL submodule code under `./sdl-native/src/main/cpp`. If you've already cloned
without the `--recurse-submodules` flag, from the project root run: `git submodule update --init --recursive`.

There are a couple prerequisites for running the example application in `./jsdl3-app`:

- Download and unpack the [jextract pre-built binaries for your platform](https://jdk.java.net/jextract/)
- Download and install [cmake binaries for your platform](https://cmake.org/download/),
  or find them from an existing IDE installation, Visual Studio, CLion, etc...

Run the following commands to get everything setup:

```shell
cd /path/to/jsdl3

# Generate SDL java bindings in `./sdl-bindings/src/main/java` by running jextract:
# - which of [jextract, jextract.ps1, jextract.bat] to run depends on your platform, this assumes windows in powershell
/path/to/jextract/jextract.ps1 --library SDL3     `
  --target-package org.libsdl.sdl                 `
  --include-dir ./sdl-native/src/main/cpp/include `
  --output      ./sdl-bindings/src/main/java      `
  SDL3/SDL3.h
  
# Configure SDL build scripts by running cmake:
/path/to/cmake.exe `
  -S ./sdl-native/src/main/cpp `
  -B ./sdl-native/build/sdl

# Build SDL shared library by running cmake:
/path/to/cmake.exe --build ./sdl-native/build/sdl --parallel
```

There are several gradle tasks in this project that attempt to simplify the setup steps by downloading,
unpacking, and running the `jextract` command to generate bindings, and running `cmake` commands to
configure and build the SDL shared library. You can try running those to start with, but it assumes
that at least `cmake` is installed and available to invoke from your `PATH`.

```shell
cd /path/to/jsdl3

# Run the gradle tasks to generate bindings and build the sdl shared library
./gradlew sdl-bindings:bindings sdl-native:libsdl

# If that worked you should have:
# - many java files in ./sdl-bindings/src/main/java/org/libsdl/sdl
# - a shared library under ./sdl-native/build/sdl with some differences depending on your platform
#   - if on windows, check under Debug/ or Release/ for an SDL3.dll
#   - otherwise look for a libSDL3.so or libSDL3.dylib for linux and macos respectively

# Copy the shared library from the build folder into the ./jsdl3-app root,
# assuming you're on windows with a debug build, this would be:
cp ./sdl-native/build/Debug/SDL3.dll ./jsdl3-app

# Launch the example app:
./gradlew jsdl3-app:run
```

### Project Setup Process

1) Created this repo as a multi-module gradle project by running `gradle init` in an empty root folder
    - added a root `build.gradle.kts` file since it wasn't included by default
    - modified `settings.gradle.kts` for the following gradle module name changes:
       - renamed the generated `app` module to `jsdl3-app`, as it will contain a test application
       - renamed the generated `list` module to `sdl-native` to contain SDL code and build config
       - renamed the generated `utilities` module to `sdl-bindings` to contain SDL java bindings generated by `jextract`
    - replaced the `jsdl3-app` module default package and source with `net.bplo.jsdl3.App`
    - removed generated packages and source from `sdl-native` and `sdl-bindings` modules
2) Add [SDL](https://github.com/libsdl-org/SDL) as a git submodule under the `sdl-native` gradle module
    - add and initialize the new git submodule:
        - `git submodule add https://github.com/libsdl-org/SDL.git ./sdl-native/src/main/cpp`
    - pin submodule to target commit for tag [`release-3.2.0`](https://github.com/libsdl-org/SDL/tree/release-3.2.0)
        - `pushd ./sdl-native/src/main/cpp && git checkout release-3.2.0 && popd`
        - `git submodule update`
3) Spend lots of time making :kissing: faces at gradle to create build tasks related to `jextract` in `sdl-bindings`
    - there's a lot to say about this piece, because figuring out how to use gradle is maddening
      and I learned a bunch from this process but didn't write it out as I went so that I didn't lose my mind
    - :pushpin: rework the `unpack` task to use the built-in `Tar` task type in gradle instead of `Exec`
4) Spend less time making :kissing: faces at gradle to create build tasks related to `cmake` in `sdl-native`
    - there's a lot to say here too, especially since there's the possibility of leveraging gradle's
      `cpp-application` and/or `cpp-library` plugins rather than invoking `cmake` directly in `Exec` tasks
    - also had a surprisingly amount of difficulty setting up a gradle task to copy the sdl library file
      out of `./sdl-native/build/[Debug|Release]/SDL3.dll` into `./jsdl3-app` so that it would be available
      to be loaded from the current working directory when running the app, so I just did it manually for now
5) With most of the pieces in place, I started adding more bits to the example app in `./jsdl3-app`
    - unexpectedly, there are some subtleties of working with the generated bindings that I'm figuring out as I go
    - seems like all functions can be called through `SDL_h.*`, even those that are in the `SDL_h_#.java` files
    - allocating data / structs that are used across the jvm / native boundary is done via an `Arena`,
      and the example app is currently setup with a single 'confined' arena for all SDL data,
      I plan to make some other arenas for different scopes, starting with a per-frame arena for allocating
      the `SDL_Event` used in the event handling loop
    - many structs like `SDL_FPoint, SDL_FRect` provide `allocate(arena)` and `allocateArray(arena, count)`
      methods for simple creation, though it doesn't look like there's a `free()` that I've found so far,
      likely because deallocation is handled automatically when the allocating `Arena` goes out of scope
    - some structs/types like `SDL_Window, SDL_Renderer` aren't structs in the sense of having fields
      that can be read/written, but rather they are opaque handles, so working with them involves
      allocating the memory, passing it into some SDL function to instantiate the underlying object,
      then getting the native pointer from the allocated memory for use in other methods that reference it
        - :thinking: I'm not certain that last piece is technically necessary, but I haven't tested it yet
    - for native structs with fields we need to use, the jextract generated interface provides nice
      getters and setters for the fields in a java `record` sort of style
    - for arrays of native structs, we need to use `{Type}.asSlice(arr, index)` to get a pointer to
      a single element, and then we can use the standard methods to interact with the element
