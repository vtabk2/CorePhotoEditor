cmake_minimum_required(VERSION 3.22.1)
project(adjust)

# ✅ Sử dụng chuẩn C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ✅ BẬT TOÀN BỘ TỐI ƯU TRÊN ANDROID
add_compile_options(
        -Ofast                     # tối ưu toàn diện (cao hơn -O3)
        -ffast-math                # bật tối ưu toán học (chấp nhận sai số nhỏ)
        -funroll-loops             # mở rộng vòng lặp
        -fstrict-aliasing          # cải thiện truy cập bộ nhớ
        -fomit-frame-pointer       # giảm overhead call stack
        -flto                      # link-time optimization (tối ưu toàn bộ file .o)
        -march=armv8-a             # tối ưu cho CPU ARM64
        -fno-exceptions            # tắt cơ chế exception C++ (nhanh hơn)
        -fno-rtti                  # tắt RTTI (nếu không cần dynamic_cast)
)

# ✅ Tạo thư viện native
add_library(adjust SHARED
        AdjustProcessor.cpp
        adjust_light.cpp
        adjust_color.cpp
        adjust_detail.cpp
        adjust_hsl.cpp
)

# ✅ Liên kết với thư viện hệ thống
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(adjust
        ${log-lib}
        ${jnigraphics-lib}
)
