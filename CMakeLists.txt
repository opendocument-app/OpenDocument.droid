cmake_minimum_required(VERSION 3.12.4)

FILE(GLOB SRC
    app/src/main/cpp/*.cpp
    app/src/main/cpp/*.h
)

include_directories(app/src/main/cpp/)

add_library(odr-core SHARED ${SRC})

option (ODR_TEST "" OFF)
set(ODR_TEST OFF)

add_subdirectory(OpenDocument.core)
target_link_libraries(odr-core odr-shared)
