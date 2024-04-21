cmake_minimum_required(VERSION 3.18.1)
project(odr-droid CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# make sure "which conan" points to a version of 1.x
# TODO: call FIND_PROGRAM(conan) here manually so the location is cached for later
# TODO: use proper virtual environment

if(NOT EXISTS "${CMAKE_BINARY_DIR}/conan.cmake")
    message(STATUS "Downloading conan.cmake from https://github.com/conan-io/cmake-conan")
    file(DOWNLOAD "https://raw.githubusercontent.com/conan-io/cmake-conan/0.17.0/conan.cmake"
            "${CMAKE_BINARY_DIR}/conan.cmake"
            EXPECTED_HASH SHA256=3bef79da16c2e031dc429e1dac87a08b9226418b300ce004cc125a82687baeef
            TLS_VERIFY ON)
endif()

# determine abi
set(ARCH "${CMAKE_ANDROID_ARCH_ABI}")
string(REPLACE "armeabi-v7a" "armv7" ARCH "${ARCH}")
string(REPLACE "arm64-v8a" "armv8" ARCH "${ARCH}")

include(${CMAKE_CURRENT_LIST_DIR}/app/conan.cmake)
conan_cmake_configure(GENERATORS cmake)
conan_cmake_autodetect(settings ARCH ${ARCH})
# inspired from here https://docs.conan.io/en/latest/integrations/cross_platform/android.html
configure_file(app/toolchain.cmake.in ${CMAKE_CURRENT_BINARY_DIR}/toolchain.cmake @ONLY)
conan_cmake_install(PATH_OR_REFERENCE ${CMAKE_CURRENT_LIST_DIR}
        BUILD missing
        SETTINGS_BUILD compiler.libcxx=libc++
        SETTINGS_HOST ${settings} compiler.libcxx=c++_shared
        PROFILE_BUILD default
        PROFILE_HOST ${CMAKE_CURRENT_LIST_DIR}/conanprofile.txt
        ENV CONAN_CMAKE_TOOLCHAIN_FILE=${CMAKE_CURRENT_BINARY_DIR}/toolchain.cmake
            ANDROID_NDK_ROOT=${CMAKE_ANDROID_NDK})
include(${CMAKE_CURRENT_BINARY_DIR}/conan_paths.cmake)

find_package(odr REQUIRED)

add_library(odr-core SHARED
        app/src/main/cpp/CoreWrapper.cpp)
target_include_directories(odr-core
        PRIVATE app/src/main/cpp/)
target_link_libraries(odr-core
        PRIVATE odr::odr log)
