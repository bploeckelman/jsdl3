package net.bplo.jsdl3;

import org.libsdl.sdl.SDL_Event;
import org.libsdl.sdl.SDL_FPoint;
import org.libsdl.sdl.SDL_FRect;
import org.libsdl.sdl.SDL_KeyboardEvent;
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
            var windowWidth = 1280;
            var windowHeight = 720;
            // NOTE: SDL_Window and SDL_Renderer are opaque handles, not structs with accessible fields
            //  so we just allocate and use them as pointers rather than their actual type
            var windowMem = arena.allocate(ValueLayout.ADDRESS);
            var rendererMem = arena.allocate(ValueLayout.ADDRESS);
            if (!SDL_h.SDL_CreateWindowAndRenderer(title, windowWidth, windowHeight, flags, windowMem, rendererMem)) {
                var error = SDL_h.SDL_GetError();
                throw new RuntimeException("SDL_CreateWindowAndRenderer failed: " + error);
            }

            // get native pointers to SDL_Window and SDL_Renderer structs
            var window = windowMem.get(ValueLayout.ADDRESS, 0);
            var renderer = rendererMem.get(ValueLayout.ADDRESS, 0);

            // setup data for a 'scene' ---------------------------------------
            var NUM_POINTS = 500;
            var MIN_PIXELS_PER_SEC = 30;
            var MAX_PIXELS_PER_SEC = 120;
            var RECT_HALF_SIZE = 3f;
            var pointSpeeds = new float[NUM_POINTS];
            var points = SDL_FPoint.allocateArray(NUM_POINTS, arena);
            var rects = SDL_FRect.allocateArray(NUM_POINTS, arena);
            for (int i = 0; i < NUM_POINTS; i++) {
                pointSpeeds[i] = MIN_PIXELS_PER_SEC + SDL_h.SDL_randf() * (MAX_PIXELS_PER_SEC - MIN_PIXELS_PER_SEC);

                var point = SDL_FPoint.asSlice(points, i);
                var x = SDL_h.SDL_randf() * (float) windowWidth;
                var y = SDL_h.SDL_randf() * (float) windowHeight;
                SDL_FPoint.x(point, x);
                SDL_FPoint.y(point, y);

                var rect = SDL_FRect.asSlice(rects, i);
                SDL_FRect.x(rect, x - RECT_HALF_SIZE);
                SDL_FRect.y(rect, y - RECT_HALF_SIZE);
                SDL_FRect.w(rect, 2 * RECT_HALF_SIZE);
                SDL_FRect.h(rect, 2 * RECT_HALF_SIZE);
            }

            // pretend to do work ---------------------------------------------
            // TODO(brian): could try creating a per-frame arena inside while(running)
            //  and using that for allocations with a shorter lifetime, like for event polling
            var event = arena.allocate(SDL_Event.layout());

            var prevTicks = SDL_h.SDL_GetTicks();
            var running = true;
            while (running) {
                // update -------------
                // handle input and events
                while (SDL_h.SDL_PollEvent(event)) {
                    int type = SDL_Event.type(event);

                    if (type == SDL_h.SDL_EVENT_QUIT()) {
                        running = false;
                    } else if (type == SDL_h.SDL_EVENT_KEY_UP()) {
                        var key = SDL_Event.key(event);
                        if (SDL_KeyboardEvent.scancode(key) == SDL_h.SDL_SCANCODE_ESCAPE()) {
                            running = false;
                        }
                    }
                }

                // update 'scene'
                var ticks = SDL_h.SDL_GetTicks();
                var now = ((double) ticks) / 1000.0;
                var elapsed = ((float) (ticks - prevTicks)) / 1000f;
                for (int i = 0; i < NUM_POINTS; i++) {
                    var dist = elapsed * pointSpeeds[i];
                    var point = SDL_FPoint.asSlice(points, i);
                    var x = SDL_FPoint.x(point) + dist;
                    var y = SDL_FPoint.y(point) + dist;

                    SDL_FPoint.x(point, x);
                    SDL_FPoint.y(point, y);

                    // went offscreen, wrap around
                    if (x >= windowWidth || y >= windowHeight) {
                        var coinFlip = (SDL_h.SDL_rand(2) == 0);
                        if (coinFlip) {
                            SDL_FPoint.x(point, SDL_h.SDL_randf() * (float) windowWidth);
                            SDL_FPoint.y(point, 0);
                        } else {
                            SDL_FPoint.x(point, 0);
                            SDL_FPoint.y(point, SDL_h.SDL_randf() * (float) windowHeight);
                        }
                        pointSpeeds[i] = MIN_PIXELS_PER_SEC + SDL_h.SDL_randf() * (MAX_PIXELS_PER_SEC - MIN_PIXELS_PER_SEC);
                    }

                    x = SDL_FPoint.x(point);
                    y = SDL_FPoint.y(point);
                    var rect = SDL_FRect.asSlice(rects, i);
                    SDL_FRect.x(rect, x - RECT_HALF_SIZE);
                    SDL_FRect.y(rect, y - RECT_HALF_SIZE);
                }

                // draw ---------------
                // calc a smooth transition of colors for the background
                var r = (float) (0.5 + 0.5 * SDL_h.SDL_sin(now));
                var g = (float) (0.5 + 0.5 * SDL_h.SDL_sin(now + SDL_h.SDL_PI_D() * 2 / 3));
                var b = (float) (0.5 + 0.5 * SDL_h.SDL_sin(now + SDL_h.SDL_PI_D() * 4 / 3));
                SDL_h.SDL_SetRenderDrawColorFloat(renderer, r, g, b, SDL_h.SDL_ALPHA_OPAQUE_FLOAT());

                // clear the screen for a new frame
                SDL_h.SDL_RenderClear(renderer);

                // draw rectangles for each point, with a color that is the inverse of the background color
                float ir = (1f - r), ig = (1f - g), ib = (1f - b);
                SDL_h.SDL_SetRenderDrawColorFloat(renderer, ir, ig, ib, SDL_h.SDL_ALPHA_OPAQUE_FLOAT());
                SDL_h.SDL_RenderFillRects(renderer, rects, NUM_POINTS);

                // draw points on top of the rects, black or white depending how bright the background color is
                var bgBrightness = 0.2126f * r + 0.7152f * g + 0.0722f * b;
                if (bgBrightness <= 0.5f) {
                    SDL_h.SDL_SetRenderDrawColorFloat(renderer, 0, 0, 0, SDL_h.SDL_ALPHA_OPAQUE_FLOAT());
                } else {
                    SDL_h.SDL_SetRenderDrawColorFloat(renderer, 1, 1, 1, SDL_h.SDL_ALPHA_OPAQUE_FLOAT());
                }
                SDL_h.SDL_RenderPoints(renderer, points, NUM_POINTS);

                SDL_h.SDL_RenderPresent(renderer);

                prevTicks = ticks;
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
