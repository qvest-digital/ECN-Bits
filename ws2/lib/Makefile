# -*- mode: make -*-
#-
# Copyright © 2020, 2021, 2023
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

CPPFLAGS=	$(CPPFLAGS) -DECNBITS_INTERNAL

MKLIB=		ecn-bitw

SRCS=		$(SRCS) desc.c
SRCS=		$(SRCS) hll.c
SRCS=		$(SRCS) hll_mono.c
SRCS=		$(SRCS) mkcmsg.c
SRCS=		$(SRCS) prep.c
SRCS=		$(SRCS) rdmsg.c
SRCS=		$(SRCS) recv.c
SRCS=		$(SRCS) recvfrom.c
SRCS=		$(SRCS) recvmsg.c
SRCS=		$(SRCS) short.c
SRCS=		$(SRCS) stoaf.c
SRCS=		$(SRCS) winrecv.c
SRCS=		$(SRCS) winsend.c

!include ../NMAKE.mak

$(OBJS): ..$Yinc$Yecn-bitw.h
