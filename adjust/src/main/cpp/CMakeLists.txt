cmake_minimum_required(VERSION 3.22)
project(adjust LANGUAGES CXX)

# =========================
# Options
# =========================
option(ADJUST_FAST_MATH "Enable fast-math (may reduce color accuracy)" OFF)
option(ADJUST_WARN_AS_ERRORS "Treat warnings as errors" OFF)
option(ADJUST_DEBUG_SANITIZERS "Enable ASAN/UBSAN in Debug" OFF)

# =========================
# C++ standard
# =========================
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# =========================
# Library
# =========================
add_library(adjust SHARED
        AdjustProcessor.cpp
        adjust_light.cpp
        adjust_color.cpp
        adjust_detail.cpp
        adjust_hsl.cpp
)

# Android system libs
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(android-lib android)
target_link_libraries(adjust PRIVATE
        ${log-lib}
        ${jnigraphics-lib}
        ${android-lib}
)

# =========================
# Visibility & PIC
# =========================
# (PIC mặc định với SHARED, nhưng ta vẫn set rõ ràng)
set_target_properties(adjust PROPERTIES
        POSITION_INDEPENDENT_CODE ON
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN ON
)

# =========================
# Warnings
# =========================
set(_WARN_FLAGS
        -Wall -Wextra -Wconversion -Wsign-conversion -Wshadow -Wundef
        -Wdouble-promotion -Wformat=2 -Wno-gnu-zero-variadic-macro-arguments
)
if(ADJUST_WARN_AS_ERRORS)
    list(APPEND _WARN_FLAGS -Werror)
endif()
target_compile_options(adjust PRIVATE ${_WARN_FLAGS})

# =========================
# Base compile flags (common)
# =========================
# Không dùng -Ofast: giữ độ chính xác gamma/tone
set(_BASE_OPT_FLAGS "")
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    list(APPEND _BASE_OPT_FLAGS
            -O3                 # tối ưu mạnh nhưng an toàn hơn -Ofast
            -fstrict-aliasing
            -fomit-frame-pointer
            -ffunction-sections -fdata-sections
    )
else()
    list(APPEND _BASE_OPT_FLAGS
            -O0 -g3
            -D_GLIBCXX_ASSERTIONS
    )
endif()

# Fast-math toggle
if(ADJUST_FAST_MATH)
    list(APPEND _BASE_OPT_FLAGS -ffast-math)
endif()

# RTTI/Exceptions: để ON mặc định cho debug dễ hơn; nếu muốn tắt thì mở 2 dòng dưới
# list(APPEND _BASE_OPT_FLAGS -fno-exceptions -fno-rtti)

target_compile_options(adjust PRIVATE ${_BASE_OPT_FLAGS})

# =========================
# ABI-specific flags
# (NDK sẽ set, ta bổ sung nhấn mạnh theo ABI)
# =========================
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(adjust PRIVATE
            -march=armv8-a
    )
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    # Với Clang trên Android, hard-float không khuyến nghị; dùng softfp + NEON
    target_compile_options(adjust PRIVATE
            -march=armv7-a -mfpu=neon -mfloat-abi=softfp
    )
    # link options cho v7a để tối ưu nhảy hàm
    target_link_options(adjust PRIVATE
            -Wl,--fix-cortex-a8
    )
endif()

# =========================
# Linker options
# =========================
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(adjust PRIVATE
            -Wl,--gc-sections
            -Wl,--icf=all
            -Wl,--strip-all
    )
else()
    # Debug: giữ symbol
    target_link_options(adjust PRIVATE
            -Wl,--gc-sections
            -Wl,--icf=all
    )
endif()

# =========================
# LTO / IPO (Release only, if supported)
# =========================
include(CheckIPOSupported)
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    check_ipo_supported(RESULT _ipo_ok OUTPUT _ipo_log)
    if(_ipo_ok)
        set_property(TARGET adjust PROPERTY INTERPROCEDURAL_OPTIMIZATION TRUE)
    endif()
endif()

# =========================
# Sanitizers (Debug)
# =========================
if(CMAKE_BUILD_TYPE STREQUAL "Debug" AND ADJUST_DEBUG_SANITIZERS)
    # ASAN + UBSAN giúp tìm out-of-bounds/UB trong dev
    target_compile_options(adjust PRIVATE -fsanitize=address,undefined)
    target_link_options(adjust PRIVATE -fsanitize=address,undefined)
endif()

# =========================
# Defines hiếm khi cần (gợi ý)
# =========================
# target_compile_definitions(adjust PRIVATE ADJUST_USE_PREMULT_ALPHA=1)
# target_compile_definitions(adjust PRIVATE ADJUST_LOG_VERBOSE=1)
