# WARNING! DO *NOT* USE THIS FILE FOR BUILDING!
# THIS IS *ONLY* USED FOR NDK COMPILATION, CALLED FROM SOMEPLACE ELSE!

cmake_minimum_required(VERSION 3.10)
project(ecn-bitw)

if (NOT TOPLEV)
	message(FATAL_ERROR "setting the top-level directory is mandatory")
endif (NOT TOPLEV)
include(CheckCCompilerFlag)

add_definitions(-D_REENTRANT)
include_directories(BEFORE ${TOPLEV}/inc)
check_c_compiler_flag(-Wdate-time HAS_WDATETIME)
if (HAS_WDATETIME)
	add_definitions(-Wdate-time)
endif (HAS_WDATETIME)
add_definitions(-D_FORTIFY_SOURCE=2)
add_compile_options(-Wall -Wformat)
add_compile_options(-Wextra)
check_c_compiler_flag(-fstack-protector-strong HAS_SSP2)
if (HAS_SSP2)
	add_compile_options(-fstack-protector-strong)
else (HAS_SSP2)
	check_c_compiler_flag(-fstack-protector HAS_SSP1)
	if (HAS_SSP1)
		add_compile_options(-fstack-protector)
	endif (HAS_SSP1)
endif (HAS_SSP2)
add_compile_options(-Werror=format-security)
add_definitions(-DECNBITS_INTERNAL)

add_library(ecn-bitw SHARED
	# MirBSD licenced
	${TOPLEV}/inc/ecn-bitw.h

	# MirBSD licenced
	desc.c
	hll.c
	prep.c
	rdmsg.c
	recv.c
	recvfrom.c
	recvmsg.c
	short.c
	stoaf.c

	# MIT licenced
	hll_mono.c
	)

target_link_libraries(ecn-bitw -Wl,-z,relro -Wl,-z,now -Wl,--as-needed)

check_c_compiler_flag(-ffile-prefix-map=a=b HAS_FILE_PREFIX_MAP)
if (HAS_FILE_PREFIX_MAP)
	add_definitions("-ffile-prefix-map=${TOPLEV}=«ECN-Bits»")
else (HAS_FILE_PREFIX_MAP)
	# expected with NDK r21 (LTS), sadly
	#message(WARNING "no support for -ffile-prefix-map in the compiler")
endif (HAS_FILE_PREFIX_MAP)
