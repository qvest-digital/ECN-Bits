# -*- mode: make -*-
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

TOP:=$(shell x=TOP.mk; until test -e $$x; do x=../$$x; done; echo $$x)
include ${TOP}

CPPFLAGS+=	-DECNBITS_INTERNAL

LIB=		ecn-bitw
HDRS+=		ecn-bitw.h
SHLIB_VERSION=	2.0

SRCS+=		desc.c
SRCS+=		hll.c
SRCS+=		prep.c
SRCS+=		rdmsg.c
SRCS+=		recv.c
SRCS+=		recvfrom.c
SRCS+=		recvmsg.c
SRCS+=		short.c
SRCS+=		stoaf.c

MAN+=		ECNBITS_VALID.3
MAN+=		ecnbits_prep.3
MAN+=		ecnbits_rdmsg.3
MAN+=		ecnbits_recvfrom.3
MAN+=		ecnbits_stoaf.3
MAN+=		ecnws2_recvmsg.3

MLINKS+=	ECNBITS_VALID.3 ECNBITS_BITS.3
MLINKS+=	ECNBITS_VALID.3 ECNBITS_DESC.3
MLINKS+=	ECNBITS_VALID.3 ECNBITS_DSCP.3
MLINKS+=	ECNBITS_VALID.3 ECNBITS_TCOCT.3
MLINKS+=	ecnbits_rdmsg.3 ecnbits_recvmsg.3
MLINKS+=	ecnbits_recvfrom.3 ecnbits_read.3
MLINKS+=	ecnbits_recvfrom.3 ecnbits_recv.3
MLINKS+=	ecnws2_recvmsg.3 ecnws2_sendmsg.3

CATS:=		$(addsuffix .txt,$(basename ${MAN}))
cats:
	-rm -f ${CATS}
	for man in ${MAN}; do \
		cat=$${man%.*}.txt; \
		set -e; set -o pipefail; \
		nroff -Tascii -mdoc -c "$$man" | col -bx >"$$cat"; \
	done
	@ls -l ${CATS}

include ${TOP}/BOTTOM.mk
