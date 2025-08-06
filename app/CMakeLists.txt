cmake_minimum_required(VERSION 3.18.1)
project(odr-droid CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(odrcore REQUIRED)

add_library(tmpfile_hack STATIC
        src/main/cpp/tmpfile_hack.cpp)
target_include_directories(tmpfile_hack
        PRIVATE src/main/cpp)
target_link_libraries(tmpfile_hack
        PRIVATE log)

add_library(odr-core SHARED
        src/main/cpp/core_wrapper.cpp)
target_include_directories(odr-core
        PRIVATE src/main/cpp)
target_link_libraries(odr-core
        PRIVATE odrcore::odrcore tmpfile_hack log)
