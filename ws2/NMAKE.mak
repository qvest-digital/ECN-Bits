# -*- mode: make -*-
#-
# Copyright © 2012
#	mirabilos <tg@mirbsd.org>
# Copyright © 2013
#	mirabilos <thorsten.glaser@teckids.org>
# Copyright © 2020, 2021
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

BKSL=^\

!IF [cmd /C IF EXIST *.obj exit 1]
CLEANFILES=	$(CLEANFILES) *.obj
!ENDIF

!IF [cmd /C IF EXIST *.ilk exit 1]
CLEANFILES=	$(CLEANFILES) *.ilk
!ENDIF

!IF [cmd /C IF EXIST *.pdb exit 1]
CLEANFILES=	$(CLEANFILES) *.pdb
!ENDIF

!IF [..$(BKSL)..$(BKSL)test-git.bat /q]
!ERROR git repository clone consistency check failed
!ENDIF

!IFNDEF CC
CC=		cl.exe
!ENDIF
!IFNDEF CFLAGS
CFLAGS=		/O2
!ENDIF
CFLAGS=		$(CFLAGS) /nologo

CPPFLAGS=	$(CPPFLAGS) -D_REENTRANT -D_WIN32_WINNT=0x0600 -I../inc
!IFNDEF NOPIC
CPPFLAGS=	$(CPPFLAGS) -DECNBITS_WIN32_DLL
!ENDIF
CPPFLAGS=	$(CPPFLAGS) -I../util
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
!IFNDEF NOPIC
LIBS=		$(LIBS) ecn-bitw_imp.lib Ws2_32.lib
!ELSE
LIBS=		$(LIBS) ecn-bitw.lib Ws2_32.lib
!ENDIF
!IF EXISTS($(PROG).exe)
CLEANFILES=	$(CLEANFILES) $(PROG).exe
!ENDIF
!IF EXISTS(..$(BKSL)BIN$(BKSL)$(PROG).exe)
CLEANFILES=	$(CLEANFILES) ..$(BKSL)BIN$(BKSL)$(PROG).exe
!ENDIF
!IFNDEF DPADD
!IFNDEF NOPIC
DPADD=		../lib/ecn-bitw_imp.lib
!ELSE
DPADD=		../lib/ecn-bitw.lib
!ENDIF
!ENDIF
all: $(PROG).exe
$(PROG).exe: $(OBJS) $(DPADD)
	IF EXIST ..$(BKSL)BIN$(BKSL)$@ (DEL ..$(BKSL)BIN$(BKSL)$@)
	$(CC) $(CFLAGS) $(LDFLAGS) /Fe$@ $(OBJS) $(LIBS) /link $(LINKFLAGS)
	COPY $@ ..$(BKSL)BIN$(BKSL)$@
!ENDIF

!IFDEF MKLIB
!IF EXISTS($(MKLIB).lib)
CLEANFILES=	$(CLEANFILES) $(MKLIB).lib
!ENDIF
!IF EXISTS($(MKLIB)_imp.exp)
CLEANFILES=	$(CLEANFILES) $(MKLIB)_imp.exp
!ENDIF
!IF EXISTS($(MKLIB)_imp.lib)
CLEANFILES=	$(CLEANFILES) $(MKLIB)_imp.lib
!ENDIF
!IF EXISTS($(MKLIB).dll)
CLEANFILES=	$(CLEANFILES) $(MKLIB).dll
!ENDIF
!IF EXISTS(..$(BKSL)BIN$(BKSL)$(MKLIB).dll)
CLEANFILES=	$(CLEANFILES) ..$(BKSL)BIN$(BKSL)$(MKLIB).dll
!ENDIF
!IFNDEF DPADD
DPADD=
!ENDIF
!IFNDEF NOPIC
LIBS=		$(LIBS) Ws2_32.lib
all: $(MKLIB).dll
$(MKLIB).dll: $(OBJS) $(MKLIB).def $(DPADD)
	IF EXIST ..$(BKSL)BIN$(BKSL)$@ (DEL ..$(BKSL)BIN$(BKSL)$@)
	$(CC) $(CFLAGS) $(LDFLAGS) /LD /Fe$@ $(OBJS) $(LIBS) /link /DEF:$(MKLIB).def /IMPLIB:$(MKLIB)_imp.lib $(LINKFLAGS)
	COPY $@ ..$(BKSL)BIN$(BKSL)$@
!ELSE
all: $(MKLIB).lib
$(MKLIB).lib: $(OBJS) $(DPADD)
	lib.exe /OUT:$@ $(OBJS)
!ENDIF
!ENDIF

clean:
!IFDEF CLEANFILES
	-del $(CLEANFILES)
!ENDIF
