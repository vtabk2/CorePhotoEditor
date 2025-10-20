cmake_minimum_required(VERSION 3.22.1)
project(adjust)

add_library(adjust SHARED
        AdjustProcessor.cpp
        adjust_light.cpp
        adjust_color.cpp
        adjust_detail.cpp
        adjust_hsl.cpp
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(adjust
        ${log-lib}
        ${jnigraphics-lib}
)
