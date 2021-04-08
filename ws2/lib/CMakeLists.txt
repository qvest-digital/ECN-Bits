# WARNING! DO *NOT* USE THIS FILE FOR BUILDING!
# THIS IS *ONLY* USED FOR NDK COMPILATION VIA android/ BUT NO PROPER
# BUILD OF THE ecn-bitw LIBRARY FOR GENERIC USE!
#-
# Copyright © 2020, 2021
#	mirabilos <t.glaser@tarent.de>
# Licensor: Deutsche Telekom
#
# Provided that these terms and disclaimer and all copyright notices
# are retained or reproduced in an accompanying document, permission
# is granted to deal in this work without restriction, including un‐
# limited rights to use, publicly perform, distribute, sell, modify,
# merge, give away, or sublicence.
#
# This work is provided “AS IS” and WITHOUT WARRANTY of any kind, to
# the utmost extent permitted by applicable law, neither express nor
# implied; without malicious intent or gross negligence. In no event
# may a licensor, author or contributor be held liable for indirect,
# direct, other damage, loss, or other issues arising in any way out
# of dealing in the work, even if advised of the possibility of such
# damage or existence of a defect, except proven that it results out
# of said person’s immediate fault when using the work as intended.
#-
# Conversion from GNUmakefile is fragile and has to verbosely build,
# which https://issuetracker.google.com/issues/184060944 makes hard,
# to figure out whether all c/ld flags are properly accounted for.

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
	add_definitions("-ffile-prefix-map=${TOPLEV}=«ecn-bitw»")
else (HAS_FILE_PREFIX_MAP)
	message(WARNING "no support for -ffile-prefix-map in the compiler")
endif (HAS_FILE_PREFIX_MAP)
