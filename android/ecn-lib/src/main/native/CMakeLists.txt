cmake_minimum_required(VERSION 3.10)
project(ecnbits-native)

option(UNDER_NDK	"Build under the Android NDK"	ON)

if (NOT TOPLEV)
	message(FATAL_ERROR "setting the top-level directory is mandatory")
endif (NOT TOPLEV)

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

if (UNDER_NDK)
	add_definitions(-DECNBITS_ALOG_TYPE=1)

	find_library(log-lib log)
	target_link_libraries(ecnbits-native ${log-lib})
	target_link_libraries(ecnbits-ndk ${log-lib})

	execute_process(COMMAND ${CMAKE_CXX_COMPILER} --version OUTPUT_VARIABLE cxx_version_full)
	string(REGEX REPLACE "^Android [^\n]* clang version ([0-9]+)\\.[0-9].*$" "\\1" cxx_version_major ${cxx_version_full})
	if (${cxx_version_major} VERSION_GREATER_EQUAL 10)
		add_definitions("-ffile-prefix-map=${TOPLEV}=«ECN-Bits»")
	else (${cxx_version_major} VERSION_GREATER_EQUAL 10)
		add_definitions(-DOLD_CLANG_SRCDIR_HACK="${TOPLEV}/")
	endif (${cxx_version_major} VERSION_GREATER_EQUAL 10)
else (UNDER_NDK)
	add_definitions(-DECNBITS_ALOG_TYPE=2)
	include(FindJNI)
	include_directories(${JNI_INCLUDE_DIRS})

	add_definitions(-D_GNU_SOURCE)

	add_definitions("-ffile-prefix-map=${TOPLEV}=«ECN-Bits»")
endif (UNDER_NDK)
