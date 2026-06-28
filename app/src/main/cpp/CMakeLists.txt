cmake_minimum_required(VERSION 3.22.1)

project("oflix_secrets")

add_library(
        oflix_secrets
        SHARED
        secrets.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        oflix_secrets
        ${log-lib})
