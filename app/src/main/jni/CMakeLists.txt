cmake_minimum_required(VERSION 3.22.1)
project("megaeffects")

# Only compile jni_bridge.c - it includes render_engine.c internally
add_library(
    render_engine
    SHARED
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
