cmake_minimum_required(VERSION 3.18.1)
project(odr-droid CXX)

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

add_library(envvar SHARED src/main/cpp/EnvVar.cc)

add_library(pdf2htmlEX-android SHARED src/main/cpp/pdf2htmlEX.cc src/main/cpp/CCharGC.h)
find_package(pdf2htmlEX REQUIRED CONFIG)
target_link_libraries(pdf2htmlEX-android
  pdf2htmlex::pdf2htmlex
  log
)

FILE(MAKE_DIRECTORY "${CMAKE_CURRENT_LIST_DIR}/build/assets")
function(copy_assets sourcevar dstsubdir)
  if(DEFINED "${sourcevar}")
    FILE(REMOVE_RECURSE "${CMAKE_CURRENT_LIST_DIR}/build/assets/${dstsubdir}")
    FILE(CREATE_LINK "${${sourcevar}}" "${CMAKE_CURRENT_LIST_DIR}/build/assets/${dstsubdir}" COPY_ON_ERROR SYMBOLIC)
  endif()
endfunction()

copy_assets("pdf2htmlex_RES_DIRS_DEBUG" "pdf2htmlEX")
copy_assets("pdf2htmlex_RES_DIRS_RELEASE" "pdf2htmlEX")
copy_assets("pdf2htmlex_RES_DIRS_RELWITHDEBINFO" "pdf2htmlEX")
copy_assets("pdf2htmlex_RES_DIRS_MINSIZEREL" "pdf2htmlEX")

copy_assets("poppler-data_RES_DIRS_DEBUG" "poppler-data")
copy_assets("poppler-data_RES_DIRS_RELEASE" "poppler-data")
copy_assets("poppler-data_RES_DIRS_RELWITHDEBINFO" "poppler-data")
copy_assets("poppler-data_RES_DIRS_MINSIZEREL" "poppler-data")

copy_assets("fontconfig_RES_DIRS_DEBUG" "fontconfig")
copy_assets("fontconfig_RES_DIRS_RELEASE" "fontconfig")
copy_assets("fontconfig_RES_DIRS_RELWITHDEBINFO" "fontconfig")
copy_assets("fontconfig_RES_DIRS_MINSIZEREL" "fontconfig")
