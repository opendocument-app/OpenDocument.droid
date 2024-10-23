cmake_minimum_required(VERSION 3.18.1)
project(odr-droid LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(odrcore REQUIRED)

add_library(odr-core SHARED
        src/main/cpp/CoreWrapper.cpp)
target_include_directories(odr-core
        PRIVATE src/main/cpp)
target_link_libraries(odr-core
        PRIVATE odrcore::odrcore log)

add_library(envvar SHARED src/main/cpp/EnvVar.c)
set_target_properties(envvar PROPERTIES LINKER_LANGUAGE C)
