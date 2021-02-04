cmake_minimum_required(VERSION 3.10)
project(ecnbits-jdkjni)

if (NOT TOPLEV)
	message(FATAL_ERROR "setting the top-level directory is mandatory")
endif (NOT TOPLEV)

add_compile_options(-fvisibility=hidden)
add_compile_options(-Wall -Wextra -Wformat)

add_library(ecnbits-jdkjni SHARED
	# MirBSD licenced
	alog.h
	udp.c

	# separate module, Apache 2 licenced
	nh.h
	nh_cxx.cc
	)

include(FindJNI)
include_directories(${JNI_INCLUDE_DIRS})

add_definitions(-D_GNU_SOURCE)

include(CheckCCompilerFlag)
check_c_compiler_flag(-ffile-prefix-map=a=b HAS_FILE_PREFIX_MAP)
if (HAS_FILE_PREFIX_MAP)
	add_definitions("-ffile-prefix-map=${TOPLEV}=«ECN-Bits»")
else (HAS_FILE_PREFIX_MAP)
	add_definitions(-DOLD_CLANG_SRCDIR_HACK="${TOPLEV}/")
endif (HAS_FILE_PREFIX_MAP)
