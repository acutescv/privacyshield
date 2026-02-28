cmake_minimum_required(VERSION 3.22.1)
project("privacyshield" CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─── Build flags ───────────────────────────────────────────────
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG -march=armv8-a+fp+simd -ffast-math")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${CMAKE_CXX_FLAGS_RELEASE}")

# ─── llama.cpp options ─────────────────────────────────────────
set(LLAMA_ANDROID ON CACHE BOOL "" FORCE)
set(LLAMA_METAL OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENBLAS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# ─── Add llama.cpp as subdirectory ────────────────────────────
# Clone with: git submodule add https://github.com/ggerganov/llama.cpp src/main/cpp/llama.cpp
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    add_subdirectory(llama.cpp)
else()
    message(WARNING "llama.cpp submodule not found. Run: git submodule update --init --recursive")
    # Create stub library for compilation without submodule
    add_library(llama STATIC IMPORTED)
    add_library(ggml STATIC IMPORTED)
endif()

# ─── JNI Bridge ───────────────────────────────────────────────
add_library(
    llama_bridge
    SHARED
    llama_bridge.cpp
)

target_include_directories(llama_bridge PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)

target_link_libraries(llama_bridge
    llama
    ggml
    android
    log
    dl
)

target_compile_definitions(llama_bridge PRIVATE
    ANDROID
    __ANDROID__
)
