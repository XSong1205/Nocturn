cmake_minimum_required(VERSION 3.22.1)
project(nocturn_node LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED NOCTURN_NODE_RUNTIME)
    get_filename_component(NOCTURN_NODE_RUNTIME "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/node-runtime" ABSOLUTE)
endif()

set(NODE_LIBRARY "${NOCTURN_NODE_RUNTIME}/lib/${ANDROID_ABI}/libnode.so")
if(NOT EXISTS "${NODE_LIBRARY}")
    message(FATAL_ERROR "Missing pinned Node runtime for ${ANDROID_ABI}. Run node scripts/prepare-node-runtime.mjs first.")
endif()

add_library(node SHARED IMPORTED)
set_target_properties(node PROPERTIES IMPORTED_LOCATION "${NODE_LIBRARY}")

add_library(nocturn_node SHARED node_bridge.cpp)
target_include_directories(nocturn_node SYSTEM PRIVATE "${NOCTURN_NODE_RUNTIME}/include/node")
target_compile_options(nocturn_node PRIVATE -Wall -Wextra -Werror)
# This aligns our bridge. The upstream libnode.so is checked separately by the
# preparation script; linker flags here cannot fix an incompatible prebuilt.
target_link_options(nocturn_node PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(nocturn_node PRIVATE node log)
