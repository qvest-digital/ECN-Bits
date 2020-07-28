# -*- mode: make -*-
#-
# Copyright © 2020
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

TOP:=$(shell x=TOP.mk; until test -e $$x; do x=../$$x; done; echo $$x)
include ${TOP}

CPPFLAGS+=	-DECNBITS_INTERNAL

LIB=		ecn-bits

SRCS+=		desc.c
SRCS+=		ecn.c
SRCS+=		recv.c
SRCS+=		recvfrom.c
SRCS+=		recvmsg.c
SRCS+=		short.c

install:
	test -d $(call shellescape,${PREFIX})/include || \
	    ${INSTALL} -d -o ${BINOWN} -g ${BINGRP} -m ${DIRMODE} \
	    $(call shellescape,${PREFIX})/include
	test -d $(call shellescape,${PREFIX})/lib || \
	    ${INSTALL} -d -o ${BINOWN} -g ${BINGRP} -m ${DIRMODE} \
	    $(call shellescape,${PREFIX})/lib
	${INSTALL} -c -o ${BINOWN} -g ${BINGRP} -m ${NONBINMODE} \
	    $(call shellescape,${TOP})/inc/*.h \
	    $(call shellescape,${PREFIX})/include/
	${INSTALL} -c -o ${BINOWN} -g ${BINGRP} -m ${NONBINMODE} \
	    lib${LIB}.a $(call shellescape,${PREFIX})/lib/

include ${TOP}/BOTTOM.mk
