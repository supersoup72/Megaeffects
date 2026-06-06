cmake_minimum_required(VERSION 3.22.1)
project("megaeffects")

# Render engine - compiled with NEON optimizations for ARM64
add_library(
    render_engine
    SHARED
    render_engine.c
    jni_bridge.c
)

target_compile_options(render_engine PRIVATE
    -O3
    -march=armv8.2-a
    -mtune=cortex-a77
    -ffast-math
    -funroll-loops
    -DNDEBUG
)

target_link_libraries(render_engine
    android
    log
    m
)
