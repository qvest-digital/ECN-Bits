cmake_minimum_required(VERSION 3.4.1)

add_definitions("-fvisibility=hidden")

add_library(ecnbits-native SHARED
	udp.c
	)

add_library(ecnbits-ndk SHARED
	ecn-jni.c
	)

find_library(log-lib log)
target_link_libraries(ecnbits-native ${log-lib})
target_link_libraries(ecnbits-ndk ${log-lib})
