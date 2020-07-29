# -*- mode: make -*-
#-
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

# include ${TOP}/BOTTOM.mk

ifdef PROG
SRCS?=		${PROG}.c
endif

OBJS?=		${SRCS:.c=.o}

ifdef LIB
CLEANFILES+=	lib${LIB}.a
all: lib${LIB}.a
lib${LIB}.a: ${OBJS}
	${AR} rc $@ ${OBJS}
	${RANLIB} $@
endif

ifdef PROG
LIBS+=		-lecn-bits
MAN?=		${PROG}.1
CLEANFILES+=	${PROG}
all: ${PROG}
${PROG}: ${OBJS} ${TOP}/lib/libecn-bits.a
	${LINK.c} -o $@ ${OBJS} ${LIBS}
endif

NOMAN?=		No
ifeq (,$(filter yes yeS yEs yES Yes YeS YEs YES,${NOMAN}))
ifneq (,$(strip ${MAN} ${MLINKS}))
install: install-man
install-man: $(addprefix ${MANDIR}/man,$(subst .,,$(suffix ${MAN} ${MLINKS})))
	$(foreach m,${MAN},$(call maninstall,$m))
	@$(call mlinksinstall,${MLINKS})

define maninstall
${INSTALL} -c -o ${BINOWN} -g ${BINGRP} -m ${NONBINMODE} $(1) \
    $(addprefix ${MANDIR}/man,$(subst .,,$(suffix $(1))))/

endef
define mlinksinstall
lnk=$(call shellescape,$(word 1,$(1))); \
file=$(call shellescape,$(word 2,$(1))); \
mdir=$(call shellescape,${MANDIR}); \
l=$$mdir/man$${lnk##*.}/$$lnk; \
t=$$mdir/man$${file##*.}/$$file; \
printf '%s -> %s\n' "$$t" "$$l"; \
rm -f "$$t"; \
ln -s "$$l" "$$t"
$(if $(word 3,$(1)),$(call mlinksinstall,$(wordlist 3,99999,$(1))))

endef

PATINSTDIRS+=	${MANDIR}/man%

uninstall: uninstall-man
uninstall-man:
	rm -f $(foreach m,$(join $(addprefix ${MANDIR}/man,$(subst .,,$(suffix ${MAN} ${MLINKS}))),$(addprefix /,${MAN} ${MLINKS})),$(call shellescape,$m))
endif
endif

define mkinstdirs
$${$(1)}:
	test -d $$(call shellescape,$$@) || \
	    $${INSTALL} -d -o $${DIROWN} -g $${DIRGRP} -m $${DIRMODE} \
	    $$(call shellescape,$$@)

endef
$(foreach v,MKINSTDIRS PATINSTDIRS,$(eval $(call mkinstdirs,$v)))

install-dirs: ${MKINSTDIRS}

clean:
	-rm -f ${CLEANFILES}
