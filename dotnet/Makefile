# -*- mode: make -*-
#-
# Copyright © 2021, 2023
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
# GNU make and nmake compatible

# path slash
SLASHPRE=/
Y=$(SLASHPRE:^/=^\)

# echo doublequote
QCHARPRE=\"
Q=$(QCHARPRE:^\=)

# ¿what? ⸘WHAT‽ https://stackoverflow.com/a/57530957/2171120
# nmake only \
!IF [set __VSCMD_ARG_NO_LOGO=1] || [set VSCMD_SKIP_SENDTELEMETRY=1]
# still nmake \
!ENDIF

# compilation commands and files
LIB_NAME=	ecn-bitn.dll
LIB_SRCS=	unmngd.cs monosupp.cs
LIB_BUILD=	-t:library -out:bin$Y$(LIB_NAME) $(LIB_SRCS)
PROG_BASENAME=	client
PROG_NAME=	$(PROG_BASENAME).dll
PROG_SRCS=	client.cs
PROG_BUILD=	-out:bin$Y$(PROG_NAME) -r:bin$Y$(LIB_NAME) $(PROG_SRCS)

all:
	@echo ...............................................
	@echo : To build for Windows, Linux/BSD dotnet/Mono:
	@echo : nmake win, make dotnet, make mono
	@echo : To force rebuilding native DLLs:
	@echo : nmake windll, make gnudll
	@echo : You can also run after building:
	@echo : make ... args=$Qhost port$Q
	@echo : Use nmake instead of make on Windows.
	@echo : Crossbuild DLLs for Android with: make aar
	@echo : To erase built files: make clean
	@echo :..............................................

CLEAN=unixclean
clean: $(CLEAN:^unix=dos)
	@..$Ytest-git /q

unixclean:
	rm -f *.bat *.stp
	-cd ..$Yws2$Ylib && $(MAKE) clean
	rm -rf bin$Yarmhf bin$Yarm64 bin$Yamd64 bin$Yi386
	rm -f bin$Y*.bat bin$Y*.dll bin$Y*.runtimeconfig.json \
	    bin$Y*.so bin$Y*.so.* bin$Y*.sh

dosclean:
	if exist *.stp (del *.stp)
	-cd ..$Yws2$Ylib && $(MAKE) /nologo /$(MAKEFLAGS) clean
	-rd /s /q bin$Yarmhf bin$Yarm64 bin$Yamd64 bin$Yi386 2>nul
	if exist *.bat (del *.bat)
	if exist bin$Y*.bat (del bin$Y*.bat)
	if exist bin$Y*.dll (del bin$Y*.dll)
	if exist bin$Y*.runtimeconfig.json (del bin$Y*.runtimeconfig.json)
	rem if exist *.lib (del *.lib)
	rem if exist *.obj (del *.obj)

windll:
	@..$Ytest-git /q
	-cd ..$Yws2$Ylib && $(MAKE) /nologo /$(MAKEFLAGS) clean
	-rd /s /q bin$Yarmhf bin$Yarm64 bin$Yamd64 bin$Yi386 2>nul
	md bin$Yarmhf
	"$(VCINSTALLDIR)$YAuxiliary$YBuild$Yvcvarsall" x86_arm && \
	    cd ..$Yws2$Ylib && $(MAKE) /nologo /$(MAKEFLAGS)
	copy ..$Yws2$Ybin$Yecn-bitw.dll bin$Yarmhf$Y
	cd ..$Yws2$Ylib && $(MAKE) /nologo /$(MAKEFLAGS) clean
	md bin$Yarm64
	"$(VCINSTALLDIR)$YAuxiliary$YBuild$Yvcvarsall" x86_arm64 && \
	    cd ..$Yws2$Ylib && $(MAKE) /nologo /$(MAKEFLAGS)
	copy ..$Yws2$Ybin$Yecn-bitw.dll bin$Yarm64$Y
	cd ..$Yws2$Ylib && $(MAKE) /nologo /$(MAKEFLAGS) clean
	md bin$Yamd64
	"$(VCINSTALLDIR)$YAuxiliary$YBuild$Yvcvarsall" x86_amd64 && \
	    cd ..$Yws2$Ylib && $(MAKE) /nologo /$(MAKEFLAGS)
	copy ..$Yws2$Ybin$Yecn-bitw.dll bin$Yamd64$Y
	cd ..$Yws2$Ylib && $(MAKE) /nologo /$(MAKEFLAGS) clean
	md bin$Yi386
	"$(VCINSTALLDIR)$YAuxiliary$YBuild$Yvcvarsall" x86 && \
	    cd ..$Yws2$Ylib && $(MAKE) /nologo /$(MAKEFLAGS)
	copy ..$Yws2$Ybin$Yecn-bitw.dll bin$Yi386$Y
	cd ..$Yws2$Ylib && $(MAKE) /nologo /$(MAKEFLAGS) clean
	@echo stamp >windll.stp
	rem --- Windows DLLs built for all architectures

windll.stp:
	$(MAKE) /nologo /$(MAKEFLAGS) windll

# cf. https://stackoverflow.com/q/70238734/2171120
dncscw.stp: mkdncsc.vbs
	dotnet --info | cscript /nologo mkdncsc.vbs
	ren dncsc.ba_ dncsc.bat
	ren dnmkrtcf.ba_ dnmkrtcf.bat
	@echo stamp >dncscw.stp

win: winbuild winrun

winbuild: dncscw.stp windll.stp
	@..$Ytest-git /q
	dncsc -nologo $(LIB_BUILD)
	dncsc -nologo $(PROG_BUILD)
	rem or: dncsc -platform:anycpu32bitpreferred $(PROG_BUILD)
	dnmkrtcf >bin$Y$(PROG_BASENAME).runtimeconfig.json
	echo @echo off >bin$Y$(PROG_BASENAME).bat
	echo dotnet "%~dp0$Y$(PROG_NAME)" %* >>bin$Y$(PROG_BASENAME).bat

winrun:
	if not "$(args: =)"=="" bin$Y$(PROG_BASENAME).bat $(args)

gnudll:
	@..$Ytest-git /q
	-cd ..$Yws2$Ylib && $(MAKE) clean
	cd ..$Yws2$Ylib && $(MAKE) NOSTATIC=Yes
	cp -a ..$Yws2$Ylib$Ylibecn-bitw.so ..$Yws2$Ylib$Ylibecn-bitw.so.* bin$Y
	-cd ..$Yws2$Ylib && $(MAKE) clean
	@echo stamp >gnudll.stp

gnudll.stp:
	$(MAKE) gnudll

# cf. https://stackoverflow.com/q/70238734/2171120
dncscu.stp: mkdncsc.awk mkdncsc.sh
	dotnet --info | mksh mkdncsc.sh
	mv dncsc.ba_ dncsc.bat
	mv dnmkrtcf.ba_ dnmkrtcf.bat
	@echo stamp >dncscu.stp

dotnet: dotnetbuild dotnetrun

dotnetbuild: dncscu.stp gnudll.stp
	@..$Ytest-git /q
	.$Ydncsc.bat -nologo $(LIB_BUILD)
	.$Ydncsc.bat -nologo $(PROG_BUILD)
	.$Ydnmkrtcf.bat >bin$Y$(PROG_BASENAME).runtimeconfig.json
	printf '%s\n' '#!/bin/sh' 'mydir=$$(dirname "$$0")' \
	    'LD_LIBRARY_PATH=$$mydir$${LD_LIBRARY_PATH:+:$$LD_LIBRARY_PATH}' \
	    'export LD_LIBRARY_PATH' \
	    'exec dotnet "$$mydir/$(PROG_NAME)" "$$@"' \
	    >bin$Y$(PROG_BASENAME).sh
	chmod a+x bin$Y$(PROG_BASENAME).sh

dotnetrun:
	test -z '$(args:'='\'')' || bin$Y$(PROG_BASENAME).sh $(args)

mono: monobuild monorun

monobuild: gnudll.stp
	@..$Ytest-git /q
	mcs -nologo /unsafe $(LIB_BUILD)
	mcs -nologo $(PROG_BUILD)
	printf '%s\n' '#!/bin/sh' 'mydir=$$(dirname "$$0")' \
	    'LD_LIBRARY_PATH=$$mydir$${LD_LIBRARY_PATH:+:$$LD_LIBRARY_PATH}' \
	    'export LD_LIBRARY_PATH' \
	    'exec mono "$$mydir/$(PROG_NAME)" "$$@"' \
	    >bin$Y$(PROG_BASENAME)-mono.sh
	chmod a+x bin$Y$(PROG_BASENAME)-mono.sh

monorun:
	test -z '$(args:'='\'')' || bin$Y$(PROG_BASENAME)-mono.sh $(args)

LSL=ls -l
DIR=$(LSL:^ls -l=dir)
aar:
	@..$Ytest-git /q
	cd ..$Yws2$Yandroid && .$Ygradlew build
	$(DIR) ..$Yws2$Yandroid$Yaar$Ybuild$Youtputs$Yaar$Y
