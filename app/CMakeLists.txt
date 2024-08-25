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

FILE(MAKE_DIRECTORY "${CMAKE_CURRENT_LIST_DIR}/build/assets")
function(copy_assets sourcevar dstsubdir)
  if(DEFINED "${sourcevar}")
    FILE(REMOVE_RECURSE "${CMAKE_CURRENT_LIST_DIR}/build/assets/${dstsubdir}")
    FILE(CREATE_LINK "${${sourcevar}}" "${CMAKE_CURRENT_LIST_DIR}/build/assets/${dstsubdir}" COPY_ON_ERROR SYMBOLIC)
  endif()
endfunction()

copy_assets("PDF2HTMLEX_RES_DIR" "pdf2htmlEX")
copy_assets("POPPLER_DATA_RES_DIR" "poppler-data")
copy_assets("WVWARE_RES_DIR" "wv")
copy_assets("FONTCONFIG_RES_DIR" "fontconfig")
