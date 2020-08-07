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

# TOP:=$(shell x=TOP.mk; until test -e $$x; do x=../$$x; done; echo $$x)
# include ${TOP}
CWD:=		$(realpath .)
TOP:=		$(realpath $(dir ${TOP}))
ifeq (,$(filter %/TOP.mk,$(wildcard ${TOP}/TOP.mk)))
$(error cannot determine top-level directory ${TOP} from cwd ${CWD})
endif
CLEANFILES:=	*.i *.o

shellescape='$(subst ','\'',$(1))'
shellexport=$(1)=$(call shellescape,${$(1)})

ifneq (,$(findstring $(origin CC),default undefined))
undefine CC
endif

# or /usr/ucb/install
INSTALL?=	install
INSTALL_STRIP?=	-s
AR?=		ar
RANLIB?=	ranlib
CC?=		cc
CPPFLAGS?=
CFLAGS?=	-O2 -g
LDFLAGS?=
LIBS?=
DEBUG?=		No

COMPILE.c=	${CC} ${CPPFLAGS} ${CFLAGS} -c
LINK.c=		${CC} ${CFLAGS} ${LDFLAGS}

run_cmd=	$(if $(shell TMP=.tmp.$(call shellescape,$@).$$$$.; \
		($(1)) >/dev/null 2>&1 || echo f; rm -f "$$TMP"*),$(3),$(2))
cc_opt_:=	0
define cc_opt
cc_opt_:=	$(shell expr ${cc_opt_} + 1)
cc_opt_${cc_opt_}:=$$(call run_cmd,$${COMPILE.c} -Werror $(2) -o "$$$$TMP"o -xc /dev/null,$(2),$(3))
$(1)+=		$${cc_opt_${cc_opt_}}
endef

CPPFLAGS+=	-D_REENTRANT
CPPFLAGS+=	-I$(call shellescape,${TOP})/inc
CPPFLAGS+=	-D_FORTIFY_SOURCE=2
CFLAGS+=	-Wall -Wformat
$(eval $(call cc_opt,CFLAGS,-Wdate-time))
CFLAGS+=	-Wextra
$(eval $(call cc_opt,CFLAGS,-fstack-protector-strong,-fstack-protector))
CFLAGS+=	-Werror=format-security
LDFLAGS+=	-Wl,-z,relro -Wl,-z,now
LDFLAGS+=	-Wl,--as-needed

ifeq (,$(filter no nO No NO,${DEBUG}))
$(eval $(call cc_opt,CFLAGS,-Og,-O))
$(eval $(call cc_opt,CFLAGS,-g3,-g))
CPPFLAGS+=	-DDEBUG
endif

PREFIX?=	/usr/local
BINDIR?=	${PREFIX}/bin
LIBDIR?=	${PREFIX}/lib
ifeq (/usr,${PREFIX})
MANDIR?=	${PREFIX}/share/man
else
MANDIR?=	${PREFIX}/man
endif
BINOWN?=	0
BINGRP?=	0
BINMODE?=	755
NONBINMODE?=	644
DIROWN?=	${BINOWN}
DIRGRP?=	${BINGRP}
DIRMODE?=	755
MANOWN?=	${BINOWN}
MANGRP?=	${BINGRP}
MANMODE?=	${NONBINMODE}

all:
install: install-dirs
uninstall:
clean:

.PHONY: all install uninstall clean

.SUFFIXES: .c .i .o .so

.c.i:
	${CC} ${CPPFLAGS} -E -dD -o $@ $<

.c.o:
	${COMPILE.c} $<

.c.so:
	${COMPILE.c} -fPIC -DPIC -o $@ $<
