cmake_minimum_required(VERSION 3.12.4)
project(odr-droid CXX)
set(CMAKE_CXX_STANDARD 17)

include(${CMAKE_TOOLCHAIN_FILE})

# determine abi
set(ARCH "${CMAKE_ANDROID_ARCH_ABI}")
string(REPLACE "armeabi-v7a" "armv7" ARCH "${ARCH}")
string(REPLACE "arm64-v8a" "armv8" ARCH "${ARCH}")

# determine api level
set(API_LEVEL "${CMAKE_SYSTEM_VERSION}")
# TODO above does not work somehow
set(API_LEVEL "19")

include(${CMAKE_CURRENT_LIST_DIR}/app/conan.cmake)
conan_cmake_configure(GENERATORS cmake)
conan_cmake_autodetect(settings ARCH ${ARCH})
#list(APPEND settings "os=Android" "os.api_level=19")
# inspired from here https://docs.conan.io/en/latest/integrations/cross_platform/android.html
conan_cmake_install(PATH_OR_REFERENCE ${CMAKE_CURRENT_LIST_DIR}/OpenDocument.core
        BUILD missing
        REMOTE conancenter
        BUILD_REQUIRES android-ndk/r20b
        SETTINGS ${settings})
        #ENV CONAN_CMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
        #ENV CHOST=${ANDROID_TOOLCHAIN_NAME}
        #ENV AR=${ANDROID_TOOLCHAIN_PREFIX}-ar
        #ENV AS=${ANDROID_TOOLCHAIN_PREFIX}-as
        #ENV RANLIB=${ANDROID_TOOLCHAIN_PREFIX}-ranlib
        #ENV CC=${ANDROID_TOOLCHAIN_PREFIX}${API_LEVEL}-clang
        #ENV CXX=${ANDROID_TOOLCHAIN_PREFIX}${API_LEVEL}-clang++
        #ENV LD=${ANDROID_TOOLCHAIN_PREFIX}-ld
        #ENV STRIP=${ANDROID_TOOLCHAIN_PREFIX}-strip)

#add_library(pthread INTERFACE)

set(ODR_TEST OFF CACHE BOOL "" FORCE)
add_subdirectory(OpenDocument.core)

add_library(odr-core SHARED
        app/src/main/cpp/CoreWrapper.cpp)
target_include_directories(odr-core
        PRIVATE app/src/main/cpp/)
target_link_libraries(odr-core
        PRIVATE odr-static)
