cmake_minimum_required(VERSION 3.18.1)

project(huh_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper.cpp whisper-build EXCLUDE_FROM_ALL)

add_library(whisper_jni SHARED whisper_jni.cpp)

target_compile_options(whisper_jni PRIVATE
    -O3
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -ffunction-sections
    -fdata-sections
    -mbranch-protection=standard
)

target_link_options(whisper_jni PRIVATE
    -Wl,--gc-sections
)

target_link_libraries(whisper_jni PRIVATE
    whisper
    android
    log
)
