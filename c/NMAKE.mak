# -*- mode: make -*-
#-
# Copyright © 2012
#	mirabilos <tg@mirbsd.org>
# Copyright © 2013
#	mirabilos <thorsten.glaser@teckids.org>
# Copyright © 2020
#	mirabilos <t.glaser@tarent.de>
# Licensor: Deutsche Telekom
#
# This product was inspired by (without directly including) software
# developed by the University of California, Berkeley, and its
# contributors. 4.4BSD-Lite2 © 1993, 1994
#	The Regents of the University of California
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

all:

CLEANFILES=	$(CLEANFILES) *.obj

!IFNDEF CC
CC=		cl.exe
!ENDIF
!IFNDEF CFLAGS
CFLAGS=		/O2
!ENDIF
CFLAGS=		$(CFLAGS) /nologo

CPPFLAGS=	$(CPPFLAGS) -D_REENTRANT -I../inc
CFLAGS=		$(CFLAGS) /utf-8
CFLAGS=		$(CFLAGS) /Wall

!IFDEF DEBUG
CFLAGS=		$(CFLAGS) /Od /Zi
CPPFLAGS=	$(CPPFLAGS) -DDEBUG
!ENDIF

.c.obj:
	$(CC) $(CPPFLAGS) $(CFLAGS) /c $<

!IFDEF PROG
!IFNDEF SRCS
SRCS=		$(PROG).c
!ENDIF
LINKFLAGS=	$(LINKFLAGS) /LIBPATH:../lib
!ENDIF

!IFNDEF OBJS
OBJS=		$(SRCS:.c=.obj)
!ENDIF

!IFDEF PROG
LIBS=		$(LIBS) ecn-bits.lib
CLEANFILES=	$(CLEANFILES) $(PROG).exe
!IFNDEF DPADD
DPADD=		../lib/ecn-bits.lib
!ENDIF
all: $(PROG).exe
$(PROG).exe: $(OBJS) $(DPADD)
	$(CC) $(CFLAGS) $(LDFLAGS) /Fe$@ $(OBJS) $(LIBS) /link $(LINKFLAGS)
!ENDIF

!IFDEF LIB
CLEANFILES=	$(CLEANFILES) $(LIB).lib
!IFNDEF DPADD
DPADD=
!ENDIF
all: $(LIB).lib
$(LIB).lib: $(OBJS) $(DPADD)
	lib.exe /OUT:$@ $(OBJS)
!ENDIF

clean:
	-del $(CLEANFILES)
