# -*- mode: make -*-
#-
# Copyright © 2012
#	mirabilos <tg@mirbsd.org>
# Copyright © 2013
#	mirabilos <thorsten.glaser@teckids.org>
# Copyright © 2020, 2021, 2023
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

# ¿what? ⸘WHAT‽ https://stackoverflow.com/a/57530957/2171120
!IF [set __VSCMD_ARG_NO_LOGO=1] || [set VSCMD_SKIP_SENDTELEMETRY=1]
!ENDIF

# path slash
SLASHPRE=/
Y=$(SLASHPRE:^/=^\)

# echo doublequote
QCHARPRE=\"
Q=$(QCHARPRE:^\=)

!IF [..$Y..$Ytest-git.bat /q]
!ERROR git repository clone consistency check failed
!ENDIF

!IF [cmd /c if exist *.obj exit 1]
CLEANFILES=	$(CLEANFILES) *.obj
!ENDIF

!IF [cmd /c if exist *.ilk exit 1]
CLEANFILES=	$(CLEANFILES) *.ilk
!ENDIF

!IF [cmd /c if exist *.pdb exit 1]
CLEANFILES=	$(CLEANFILES) *.pdb
!ENDIF

!IFNDEF CC
CC=		cl.exe
!ENDIF
!IFNDEF CFLAGS
!IFNDEF DEBUG
CFLAGS=		/O2
!ENDIF
!ENDIF
CFLAGS=		$(CFLAGS) /nologo

# 2016-01-12 EOL: Windows 8
# 2023-10-10 EOL: Windows Server 2012
WINVERFLAGS=	-DWINVER=0x0602 -D_WIN32_WINNT=0x0602 -DNTDDI_VERSION=NTDDI_WIN8
# 2023-01-10 EOL: Windows 8.1
# 2023-10-10 EOL: Windows Server 2012 R2
#WINVERFLAGS=	-DWINVER=0x0603 -D_WIN32_WINNT=0x0603 -DNTDDI_VERSION=NTDDI_WINBLUE
# 2025-10-14 EOL: Windows 10 (any)
# 2027-01-12 EOL: Windows Server 2016 (NTDDI_WIN10_RS1)
#WINVERFLAGS=	-DWINVER=0x0A00 -D_WIN32_WINNT=0x0A00 -DNTDDI_VERSION=NTDDI_WIN10

CPPFLAGS=	$(CPPFLAGS) -D_REENTRANT $(WINVERFLAGS) -I..$Yinc
!IFNDEF NOPIC
CPPFLAGS=	$(CPPFLAGS) -DECNBITS_WIN32_DLL
!ENDIF
CPPFLAGS=	$(CPPFLAGS) -I..$Yutil
CFLAGS=		$(CFLAGS) /utf-8
CFLAGS=		$(CFLAGS) /Wall
CFLAGS=		$(CFLAGS) /Qspectre /wd5045

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
LINKFLAGS=	$(LINKFLAGS) /LIBPATH:..$Ylib
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
!IF EXISTS(..$Ybin$Y$(PROG).exe)
CLEANFILES=	$(CLEANFILES) ..$Ybin$Y$(PROG).exe
!ENDIF
!IFNDEF DPADD
!IFNDEF NOPIC
DPADD=		..$Ylib$Yecn-bitw_imp.lib
!ELSE
DPADD=		..$Ylib$Yecn-bitw.lib
!ENDIF
!ENDIF
all: $(PROG).exe
$(PROG).exe: $(OBJS) $(DPADD)
	@for %f in ($@ ..$Ybin$Y$@) do @if exist %f (del %f)
	$(CC) $(CFLAGS) $(LDFLAGS) /Fe$@ $(OBJS) $(LIBS) /link $(LINKFLAGS)
	copy $@ ..$Ybin$Y$@
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
!IF EXISTS(..$Ybin$Y$(MKLIB).dll)
CLEANFILES=	$(CLEANFILES) ..$Ybin$Y$(MKLIB).dll
!ENDIF
!IFNDEF DPADD
DPADD=
!ENDIF
!IFNDEF NOPIC
LIBS=		$(LIBS) Ws2_32.lib
all: $(MKLIB).dll
$(MKLIB).dll: $(OBJS) $(MKLIB).def $(DPADD)
	@for %f in ($(MKLIB)_imp.exp $(MKLIB)_imp.lib $@ ..$Ybin$Y$@) do @if exist %f (del %f)
	$(CC) $(CFLAGS) $(LDFLAGS) /LD /Fe$@ $(OBJS) $(LIBS) /link /DEF:$(MKLIB).def /IMPLIB:$(MKLIB)_imp.lib $(LINKFLAGS)
	copy $@ ..$Ybin$Y$@
!ELSE
all: $(MKLIB).lib
$(MKLIB).lib: $(OBJS) $(DPADD)
	@for %f in ($@ ..$Ybin$Y$@) do @if exist %f (del %f)
	lib.exe /OUT:$@ $(OBJS)
!ENDIF
!ENDIF

clean:
!IFDEF CLEANFILES
	-del $(CLEANFILES)
!ENDIF
