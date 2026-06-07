cmake_minimum_required(VERSION 3.22.1)
project("personai" CXX)

# ── llama.cpp submodule ───────────────────────────────────────────────────────
# Run in repo root before building:
#   git submodule add https://github.com/ggerganov/llama.cpp app/src/main/cpp/llama.cpp
#   git submodule update --init --recursive

set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

if(NOT EXISTS ${LLAMA_DIR}/CMakeLists.txt)
    message(FATAL_ERROR
        "llama.cpp not found at ${LLAMA_DIR}.\n"
        "Run: git submodule add https://github.com/ggerganov/llama.cpp app/src/main/cpp/llama.cpp")
endif()

# Build flags tuned for Snapdragon 6 Gen 1 (Revvl 7)
set(GGML_NEON       ON  CACHE BOOL "" FORCE)
set(GGML_ARM_DOTPROD ON CACHE BOOL "" FORCE)
set(GGML_ARM_I8MM   ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(LLAMA_NATIVE         OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} ${CMAKE_BINARY_DIR}/llama.cpp)

# ── JNI bridge ────────────────────────────────────────────────────────────────
add_library(personai-jni SHARED llama_jni.cpp)

target_include_directories(personai-jni PRIVATE ${LLAMA_DIR}/include ${LLAMA_DIR}/ggml/include)

target_link_libraries(personai-jni
    llama
    ggml
    android
    log
)

target_compile_options(personai-jni PRIVATE
    -march=armv8.2-a+dotprod+i8mm
    -ffast-math
    -funroll-loops
)
