cmake_minimum_required(VERSION 3.22.1)
project("megaeffects")

# ── Render engine ─────────────────────────────────────────────────────────────
add_library(render_engine SHARED jni_bridge.c)

target_compile_options(render_engine PRIVATE
    -O3 -march=armv8.2-a -mtune=cortex-a77
    -ffast-math -funroll-loops -DNDEBUG
)

target_link_libraries(render_engine android log m)

# ── TCC compiler wrapper ──────────────────────────────────────────────────────
# libtcc.so is downloaded/built by GitHub Actions and placed in jniLibs/
# We just need to link against it here
find_library(LIBTCC tcc)

if(LIBTCC)
    add_library(tcc_wrapper SHARED tcc_jni.c)
    target_link_libraries(tcc_wrapper ${LIBTCC} android log)
    message(STATUS "libtcc found: ${LIBTCC}")
else()
    # Build a stub that returns "not available"
    add_library(tcc_wrapper SHARED tcc_jni_stub.c)
    target_link_libraries(tcc_wrapper android log)
    message(STATUS "libtcc not found, using stub")
endif()
