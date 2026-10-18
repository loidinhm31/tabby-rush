cmake_minimum_required(VERSION 3.20)
project(tabbyrush_toast LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(MSVC)
    add_compile_options(/W4 /WX /permissive- /EHsc)
    # C++/WinRT projection requires /await:strict or standard C++20 coroutines
    add_compile_options(/await:strict)
endif()

# Shared library tabbyrush-toast
add_library(tabbyrush-toast SHARED
    tabbyrush-toast.h
    tabbyrush-toast.cpp
)

target_include_directories(tabbyrush-toast PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_definitions(tabbyrush-toast PRIVATE
    TABBYRUSH_TOAST_EXPORTS
)

if(WIN32)
    target_link_libraries(tabbyrush-toast PRIVATE
        windowsapp.lib
        runtimeobject.lib
        ole32.lib
    )
endif()

set_target_properties(tabbyrush-toast PROPERTIES
    OUTPUT_NAME "tabbyrush-toast"
    PREFIX ""
)

# Native unit test runner
enable_testing()

add_executable(tabbyrush-toast-test
    tabbyrush-toast-test.cpp
)

target_link_libraries(tabbyrush-toast-test PRIVATE
    tabbyrush-toast
)

add_test(NAME tabbyrush-toast-test COMMAND tabbyrush-toast-test)
