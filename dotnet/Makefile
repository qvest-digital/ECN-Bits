# -*- mode: make -*-
#-
# Copyright © 2021
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
# GNU make and nmake compatible except clean (Unix) vs. del (CMD)

# sdkver=$(dotnet --version)
sdkver=5.0.201
# fwkver=$(dotnet --list-runtimes | sed --posix -n '/^Microsoft.NETCore.App \([^ ]*\) .*$/{s//\1/p;q;}')
fwkver=5.0.4

# dotnet-sdk-5.0 installed via .deb package
dotnethome=/usr/share/dotnet
dotnetlib=$(dotnethome)/shared/Microsoft.NETCore.App/$(fwkver)
dotnet_csc=dotnet $(dotnethome)/sdk/$(sdkver)/Roslyn/bincore/csc.dll
dotnet_cscopts=-lib:$(dotnetlib) -r:netstandard.dll -r:Microsoft.CSharp.dll \
		-r:System.dll $$(cd $(dotnetlib) && for x in System.*.dll; do \
			echo -r:$$x; \
		done)

LIB_NAME=	ecn-bitn.dll
LIB_SRCS=	unmngd.cs monosupp.cs
LIB_BUILD=	-t:library -out:$(LIB_NAME) $(LIB_SRCS)
PROG_NAME=	client
PROG_SRCS=	client.cs
PROG_BUILD=	-r:$(LIB_NAME) $(PROG_SRCS)

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
	-cd ../ws2/lib && $(MAKE) clean
	rm -rf armhf arm64 amd64 i386
	rm -f *.dll *.exe *.runtimeconfig.json *.so *.so.* *.stp

dosclean:
	-cd ..$Yws2$Ylib && $(MAKE) /nologo clean
	-rd /s /q armhf arm64 amd64 i386 2>nul
	if exist *.dll (del *.dll)
	if exist *.exe (del *.exe)
	if exist *.lib (del *.lib)
	if exist *.obj (del *.obj)
	if exist *.stp (del *.stp)

windll:
	..$Ytest-git /q
	-cd ..$Yws2$Ylib && $(MAKE) /nologo clean
	-rd /s /q armhf arm64 amd64 i386 2>nul
	md armhf
	"$(VCINSTALLDIR)$YAuxiliary$YBuild$Yvcvarsall" x86_arm && \
	    cd ..$Yws2$Ylib && $(MAKE) /nologo
	copy ..$Yws2$Ybin$Yecn-bitw.dll armhf$Y
	cd ..$Yws2$Ylib && $(MAKE) /nologo clean
	md arm64
	"$(VCINSTALLDIR)$YAuxiliary$YBuild$Yvcvarsall" x86_arm64 && \
	    cd ..$Yws2$Ylib && $(MAKE) /nologo
	copy ..$Yws2$Ybin$Yecn-bitw.dll arm64$Y
	cd ..$Yws2$Ylib && $(MAKE) /nologo clean
	md amd64
	"$(VCINSTALLDIR)$YAuxiliary$YBuild$Yvcvarsall" x86_amd64 && \
	    cd ..$Yws2$Ylib && $(MAKE) /nologo
	copy ..$Yws2$Ybin$Yecn-bitw.dll amd64$Y
	cd ..$Yws2$Ylib && $(MAKE) /nologo clean
	md i386
	"$(VCINSTALLDIR)$YAuxiliary$YBuild$Yvcvarsall" x86 && \
	    cd ..$Yws2$Ylib && $(MAKE) /nologo
	copy ..$Yws2$Ybin$Yecn-bitw.dll i386$Y
	cd ..$Yws2$Ylib && $(MAKE) /nologo clean
	@echo stamp >windll.stp
	rem --- Windows DLLs built for all architectures

windll.stp:
	$(MAKE) /nologo windll

win: winbuild winrun

winbuild: windll.stp
	..$Ytest-git /q
	csc -nologo $(LIB_BUILD)
	csc -nologo $(PROG_BUILD)
	rem or: csc -platform:anycpu32bitpreferred $(PROG_BUILD)

winrun:
	if not "$(args: =)"=="" $(PROG_NAME) $(args)

gnudll:
	-cd ../ws2/lib && $(MAKE) clean
	cd ../ws2/lib && $(MAKE) NOSTATIC=Yes
	cp -a ../ws2/lib/libecn-bitw.so ../ws2/lib/libecn-bitw.so.* ./
	-cd ../ws2/lib && $(MAKE) clean
	@echo stamp >gnudll.stp

gnudll.stp:
	$(MAKE) gnudll

dotnet: dotnetbuild dotnetrun

dotnetbuild: gnudll.stp
	$(dotnet_csc) -nologo $(dotnet_cscopts) \
	    -r:Microsoft.Win32.Primitives.dll $(LIB_BUILD)
	$(dotnet_csc) -nologo $(dotnet_cscopts) \
	    -r:Microsoft.Win32.Primitives.dll $(PROG_BUILD)
	echo '{"runtimeOptions":{"framework":{"name":"Microsoft.NETCore.App","version":"$(fwkver)"}}}' \
	    >$(PROG_NAME).runtimeconfig.json

dotnetrun:
	test -z '$(args:'='\'')' || \
	    LD_LIBRARY_PATH=.$${LD_LIBRARY_PATH:+:$$LD_LIBRARY_PATH} \
	    dotnet $(PROG_NAME).exe $(args)

mono: monobuild monorun

monobuild: gnudll.stp
	mcs -nologo /unsafe $(LIB_BUILD)
	mcs -nologo $(PROG_BUILD)

monorun:
	test -z '$(args:'='\'')' || \
	    LD_LIBRARY_PATH=.$${LD_LIBRARY_PATH:+:$$LD_LIBRARY_PATH} \
	    mono $(PROG_NAME).exe $(args)

dotnethelp:
	$(dotnet_csc) -help

LSL=ls -l
DIR=$(LSL:^ls -l=dir)
aar:
	@..$Ytest-git /q
	cd ..$Yws2$Yandroid && .$Ygradlew build
	$(DIR) ..$Yws2$Yandroid$Yaar$Ybuild$Youtputs$Yaar$Y
