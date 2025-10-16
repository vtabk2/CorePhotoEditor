cmake_minimum_required(VERSION 3.22.1)
project("adjust")

add_library(
    adjust
    SHARED
    adjust.cpp
)

find_library(
    log-lib
    log
)

target_include_directories(adjust PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(
    adjust
    ${log-lib}
    jnigraphics
)
