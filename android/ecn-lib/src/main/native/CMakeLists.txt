cmake_minimum_required(VERSION 3.4.1)

add_definitions("-fvisibility=hidden")

add_library(ecnbits-ndk SHARED
	ecn-jni.c
	v1-ds.c
	v2-dc.c
	)

find_library(log-lib log)
target_link_libraries(ecnbits-ndk ${log-lib})
