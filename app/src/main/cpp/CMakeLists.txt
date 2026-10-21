cmake_minimum_required(VERSION 3.18)
project(mp3enc C)

# LAME is cross-compiled by scripts/build_lame.sh into app/src/main/cpp/lame/<abi>/ before Gradle runs.
# If it is missing the library still builds, and the app reports "MP3 encoder not available".
set(LAME_DIR ${CMAKE_SOURCE_DIR}/lame/${ANDROID_ABI})

add_library(mp3enc SHARED mp3enc.c)

if(EXISTS ${LAME_DIR}/lib/libmp3lame.a)
    target_compile_definitions(mp3enc PRIVATE HAVE_LAME=1)
    target_include_directories(mp3enc PRIVATE ${LAME_DIR}/include/lame ${LAME_DIR}/include)
    target_link_libraries(mp3enc ${LAME_DIR}/lib/libmp3lame.a m)
endif()

target_link_options(mp3enc PRIVATE -Wl,-z,max-page-size=16384)
