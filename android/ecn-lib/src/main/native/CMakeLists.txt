cmake_minimum_required(VERSION 3.4.1)
project(ecnbits-native)

option(ANDROID_NDK	"Build under the Android NDK"	ON)

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

if (ANDROID_NDK)
	add_definitions(-DECNBITS_ALOG_TYPE=1)
	find_library(log-lib log)
	target_link_libraries(ecnbits-native ${log-lib})
	target_link_libraries(ecnbits-ndk ${log-lib})
else (ANDROID_NDK)
	add_definitions(-DECNBITS_ALOG_TYPE=2)
endif (ANDROID_NDK)
