cmake_minimum_required(VERSION 3.18.1)
project(odr-droid CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# create virtual environment in project root and "pip install conan==1.*"
set(CMAKE_PREFIX_PATH "${CMAKE_CURRENT_LIST_DIR}/bin")
# or make sure "which conan" points to a version of 1.x

# TODO: call FIND_PROGRAM(conan) here manually so the location is cached for later
# TODO: use proper virtual environment

find_package(odr REQUIRED)

add_library(odr-core SHARED
        src/main/cpp/CoreWrapper.cpp)
target_include_directories(odr-core
        PRIVATE src/main/cpp)
target_link_libraries(odr-core
        PRIVATE odr::odr log)
