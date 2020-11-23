cmake_minimum_required(VERSION 3.4.1)
project(ecnbits-native)

add_compile_options(-fvisibility=hidden)
add_compile_options(-Wall -Wextra -Wformat)

add_library(ecnbits-native SHARED
	alog.h
	udp.c
	)

add_library(ecnbits-ndk SHARED
	alog.h
	ecn-jni.c
	)

find_library(log-lib log)
target_link_libraries(ecnbits-native ${log-lib})
target_link_libraries(ecnbits-ndk ${log-lib})
