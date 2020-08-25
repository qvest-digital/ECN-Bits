cmake_minimum_required(VERSION 3.4.1)

add_library(native-lib SHARED
	ecn-ndk.h

	ecn-bionic.c
	ecn-jni.cc
	)

find_library(log-lib log)
target_link_libraries(native-lib ${log-lib})
