package net.bplo.jsdl3;

import org.libsdl.sdl.SDL_Event;
import org.libsdl.sdl.SDL_h;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;

public class App {
    public static void main(String[] args) {
        try (var arena = Arena.ofConfined()) {
            // initialize sdl -------------------------------------------------
            int initFlags = SDL_h.SDL_INIT_VIDEO();
            if (!SDL_h.SDL_Init(initFlags)) {
                var error = SDL_h.SDL_GetError();
                throw new RuntimeException("SDL_Init failed: " + error);
            }
            System.out.println("SDL_Init OK");

            // create window and renderer -------------------------------------
            var title = arena.allocateFrom("sdl3-java test");
            var flags = SDL_h.SDL_WINDOW_RESIZABLE();
            var windowMem = arena.allocate(ValueLayout.ADDRESS);
            var rendererMem = arena.allocate(ValueLayout.ADDRESS);
            if (!SDL_h.SDL_CreateWindowAndRenderer(title, 1280, 720, flags, windowMem, rendererMem)) {
                var error = SDL_h.SDL_GetError();
                throw new RuntimeException("SDL_CreateWindowAndRenderer failed: " + error);
            }

            // get native pointers to SDL_Window and SDL_Renderer structs
            var window = windowMem.get(ValueLayout.ADDRESS, 0);
            var renderer = rendererMem.get(ValueLayout.ADDRESS, 0);

            // pretend to do work ---------------------------------------------
            // should have a window at this point, spin for a bit and then start shutting down
            var event = arena.allocate(SDL_Event.layout());
            var running = true;
            while (running) {
                while (SDL_h.SDL_PollEvent(event)) {
                    int eventType = SDL_Event.type(event);
                    // NOTE: this is supposed to be accessible but it's complaining that its private
                    //   so I'll just use the value explicitly for now
                    var keyUpEvent = (int) 769L; // SDL_h_3.SDL_EVENT_KEY_UP()
                    if (eventType == keyUpEvent) {
                        running = false;
                    }
                }

            }
            System.out.println("Shutting down...");

            // cleanup window and renderer ------------------------------------
            SDL_h.SDL_DestroyWindow(window);
            System.out.println("SDL_DestroyWindow OK");
            SDL_h.SDL_DestroyRenderer(renderer);
            System.out.println("SDL_DestroyRenderer OK");

            // shutdown sdl ---------------------------------------------------
            System.out.println("SDL_Quit...");
            SDL_h.SDL_Quit();
        }
    }
}
