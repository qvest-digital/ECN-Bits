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

_rm=		rm -f $(foreach arg,$(1),$(call shellescape,${arg}))
_ln=		ln -s $(call shellescape,$(1)) $(call shellescape,$(2)) || \
		    cp $(call shellescape,$(if $(filter /%,$(1)),$(1),$(abspath $(dir $(2))$(1)))) $(call shellescape,$(2))

ifdef PROG
SRCS?=		${PROG}.c

LDFLAGS+=	-L$(call shellescape,${TOP}/lib)
ifndef NO_RPATH
LDFLAGS+=	-Wl,-rpath,$(call shellescape,${TOP}/lib)
endif
endif

OBJS?=		${SRCS:.c=.o}
SOBJS?=		${OBJS:.o=.so}

ifdef LIB
DPADD?=
LIBINSTFILES+=	lib${LIB}.a
all: lib${LIB}.a
lib${LIB}.a: ${OBJS} ${DPADD}
	${AR} rc $@ ${OBJS}
	${RANLIB} $@

ifeq (,$(filter yes yeS yEs yES Yes YeS YEs YES,${NOPIC}))
CLEANFILES+=	${SOBJS}
LIBINSTFILES+=	lib${LIB}_pic.a
all: lib${LIB}_pic.a
lib${LIB}_pic.a: ${SOBJS} ${DPADD}
	${AR} rc $@ ${SOBJS}
	${RANLIB} $@

ifdef SHLIB_VERSION
ifeq (${RTLD_TYPE},dyld)
SHLIB_SONAME=	lib${LIB}.${SHLIB_VERSION}.0.dylib
SHLIB_LINK_1=	lib${LIB}.$(basename ${SHLIB_VERSION}).dylib
SHLIB_LINK_0=	lib${LIB}.dylib
else ifeq (${RTLD_TYPE},GNU)
SHLIB_SONAME=	lib${LIB}.so.${SHLIB_VERSION}.0
SHLIB_LINK_1=	lib${LIB}.so.$(basename ${SHLIB_VERSION})
SHLIB_LINK_0=	lib${LIB}.so
else
SHLIB_SONAME=	lib${LIB}.so.${SHLIB_VERSION}
endif
SHLIB_FLAGS=	${CFLAGS} -fPIC
ifeq (,$(filter ${RTLD_TYPE},dyld))
SHLIB_FLAGS+=	-Wl,--no-undefined
endif
SHLIB_FLAGS+=	${LDFLAGS}
ifeq (${RTLD_TYPE},dyld)
LINK.shlib=	${CC} ${SHLIB_FLAGS} -dynamiclib -undefined error \
		    ${SOBJS} ${LIBS} \
		    -install_name @rpath/${SHLIB_LINK_1} \
		    -compatibility_version $(basename ${SHLIB_VERSION}).0 \
		    -current_version ${SHLIB_VERSION}
else ifeq (${RTLD_TYPE},GNU)
LINK.shlib=	${CC} ${SHLIB_FLAGS} -shared \
		    ${SOBJS} -Wl,--start-group ${LIBS} -Wl,--end-group \
		    -Wl,-soname,${SHLIB_LINK_1}
else
LINK.shlib=	${CC} ${SHLIB_FLAGS} -shared \
		    ${SOBJS} -Wl,--start-group ${LIBS} -Wl,--end-group
endif
LIBINSTFILES+=	${SHLIB_SONAME}

all: ${SHLIB_SONAME}
${SHLIB_SONAME}: ${SOBJS} ${DPADD}
ifneq (,${SHLIB_LINK_1})
	@$(call _rm,${SHLIB_LINK_1} ${SHLIB_LINK_0})
	@$(call _ln,$@,${SHLIB_LINK_1})
	@$(call _ln,$@,${SHLIB_LINK_0})
endif
	${LINK.shlib} -o $@

ifneq (,${SHLIB_LINK_1})
clean: clean-liblinks
clean-liblinks:
	-rm -f ${SHLIB_LINK_1} ${SHLIB_LINK_0}
endif
endif
endif

ifdef HDRS
MKINSTDIRS+=	${PREFIX}/include
install: install-hdrs
uninstall: uninstall-hdrs
install-hdrs:
	$(foreach h,${HDRS},$(call hdrinstall,$h))
uninstall-hdrs:
	$(call _rm,$(addprefix ${PREFIX}/include/,${HDRS}))

define hdrinstall
${INSTALL} -c -o ${BINOWN} -g ${BINGRP} -m ${NONBINMODE} \
    $(call shellescape,${TOP}/inc/$(1)) \
    $(call shellescape,${PREFIX}/include/)

endef
endif

ifneq (,$(strip ${LIBDIR}))
MKINSTDIRS+=	${LIBDIR}
install: install-lib
uninstall: uninstall-lib
install-lib:
	${INSTALL} -c -o ${BINOWN} -g ${BINGRP} -m ${NONBINMODE} \
	    ${LIBINSTFILES} $(call shellescape,${LIBDIR}/)
ifneq (,${SHLIB_LINK_1})
	@$(call _rm,$(addprefix ${LIBDIR}/,${SHLIB_LINK_1} ${SHLIB_LINK_0}))
	$(call _ln,${SHLIB_SONAME},${LIBDIR}/${SHLIB_LINK_1})
	$(call _ln,${SHLIB_SONAME},${LIBDIR}/${SHLIB_LINK_0})
endif
endif

uninstall-lib:
	$(call _rm,$(addprefix ${LIBDIR}/,${LIBINSTFILES}))
ifneq (,${SHLIB_LINK_1})
	$(call _rm,$(addprefix ${LIBDIR}/,${SHLIB_LINK_1} ${SHLIB_LINK_0}))
endif

CLEANFILES+=	${LIBINSTFILES}
endif

ifdef PROG
LIBS+=		-lecn-bits
MAN?=		${PROG}.1
CLEANFILES+=	${PROG}
DPADD?=		${TOP}/lib/libecn-bits.a
all: ${PROG}
${PROG}: ${OBJS} ${DPADD}
	${LINK.c} -o $@ ${OBJS} ${LIBS}

ifneq (,$(strip ${BINDIR}))
MKINSTDIRS+=	${BINDIR}
install: install-bin
uninstall: uninstall-bin
install-bin:
	${INSTALL} -c ${INSTALL_STRIP} -o ${BINOWN} -g ${BINGRP} -m ${BINMODE} \
	    ${PROG} $(call shellescape,${BINDIR}/)

uninstall-bin:
	$(call _rm,$(addprefix ${BINDIR}/,${PROG}))
endif
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
ln -s "$$l" "$$t" || cp "$$l" "$$t"
$(if $(word 3,$(1)),$(call mlinksinstall,$(wordlist 3,99999,$(1))))

endef

PATINSTDIRS+=	${MANDIR}/man%

uninstall: uninstall-man
uninstall-man:
	$(call _rm,$(join $(addprefix ${MANDIR}/man,$(subst .,,$(suffix ${MAN} ${MLINKS}))),$(addprefix /,${MAN} ${MLINKS})))
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
	-$(call _rm,$(wildcard ${CLEANFILES}))
