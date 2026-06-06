cmake_minimum_required(VERSION 3.22.1)
project("megaeffects")

# ── Render engine ─────────────────────────────────────────────────────────────
add_library(render_engine SHARED jni_bridge.c)
target_compile_options(render_engine PRIVATE
    -O3 -march=armv8.2-a -mtune=cortex-a77
    -ffast-math -funroll-loops -DNDEBUG
)
target_link_libraries(render_engine android log m)

# ── TCC wrapper ───────────────────────────────────────────────────────────────
# libtcc.so is placed in jniLibs/arm64-v8a by GitHub Actions
# Declare it as a prebuilt shared library
set(LIBTCC_PATH "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libtcc.so")

if(EXISTS "${LIBTCC_PATH}")
    message(STATUS "Found libtcc.so at ${LIBTCC_PATH}")
    add_library(tcc SHARED IMPORTED)
    set_target_properties(tcc PROPERTIES
        IMPORTED_LOCATION "${LIBTCC_PATH}"
    )
    add_library(tcc_wrapper SHARED tcc_jni.c)
    target_link_libraries(tcc_wrapper tcc android log)
    message(STATUS "Building tcc_wrapper with libtcc")
else()
    message(STATUS "libtcc.so not found at ${LIBTCC_PATH}, using stub")
    add_library(tcc_wrapper SHARED tcc_jni_stub.c)
    target_link_libraries(tcc_wrapper android log)
endif()
