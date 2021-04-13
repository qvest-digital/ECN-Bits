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

LIB_NAME=	unmngd.dll
LIB_SRCS=	unmngd.cs monosupp.cs
LIB_BUILD=	-t:library $(LIB_SRCS)
PROG_NAME=	client
PROG_SRCS=	client.cs
PROG_BUILD=	-r:$(LIB_NAME) $(PROG_SRCS)

# backslash (for DOS pathnames)
BKSL=^\

# ↑ above line intentionally left blank!

# echo doublequote
QCHARPRE=\"
Q=$(QCHARPRE:^\=)

all:
	@echo choose one:
	@echo Windows: nmake win, nmake del
	@echo Linux: make dotnet, make mono, make clean
	@echo invoke with: args=$Qhost port$Q
	@echo nmake windll or make gnulinux to rebuild the DLLs

clean:
	rm -rf armhf arm64 amd64 i386
	rm -f *.dll *.exe *.runtimeconfig.json *.so *.so.* *.stp

del:
	-cd ..$(BKSL)ws2$(BKSL)lib && $(MAKE) clean
	-rd /s /q armhf arm64 amd64 i386 2>nul
	if exist *.dll (del *.dll)
	if exist *.exe (del *.exe)
	if exist *.lib (del *.lib)
	if exist *.obj (del *.obj)
	if exist *.stp (del *.stp)
	@..$(BKSL)test-git.bat /q

windll:
	..$(BKSL)test-git.bat /q
	-cd ..$(BKSL)ws2$(BKSL)lib && $(MAKE) clean
	-rd /s /q armhf arm64 amd64 i386 2>nul
	md armhf
	"$(VCINSTALLDIR)$(BKSL)Auxiliary$(BKSL)Build$(BKSL)vcvarsall.bat" x86_arm && \
	    cd ..$(BKSL)ws2$(BKSL)lib && $(MAKE)
	copy ..$(BKSL)ws2$(BKSL)bin$(BKSL)ecn-bitw.dll armhf$(BKSL)
	cd ..$(BKSL)ws2$(BKSL)lib && $(MAKE) clean
	md arm64
	"$(VCINSTALLDIR)$(BKSL)Auxiliary$(BKSL)Build$(BKSL)vcvarsall.bat" x86_arm64 && \
	    cd ..$(BKSL)ws2$(BKSL)lib && $(MAKE)
	copy ..$(BKSL)ws2$(BKSL)bin$(BKSL)ecn-bitw.dll arm64$(BKSL)
	cd ..$(BKSL)ws2$(BKSL)lib && $(MAKE) clean
	md amd64
	"$(VCINSTALLDIR)$(BKSL)Auxiliary$(BKSL)Build$(BKSL)vcvarsall.bat" x86_amd64 && \
	    cd ..$(BKSL)ws2$(BKSL)lib && $(MAKE)
	copy ..$(BKSL)ws2$(BKSL)bin$(BKSL)ecn-bitw.dll amd64$(BKSL)
	cd ..$(BKSL)ws2$(BKSL)lib && $(MAKE) clean
	md i386
	"$(VCINSTALLDIR)$(BKSL)Auxiliary$(BKSL)Build$(BKSL)vcvarsall.bat" x86 && \
	    cd ..$(BKSL)ws2$(BKSL)lib && $(MAKE)
	copy ..$(BKSL)ws2$(BKSL)bin$(BKSL)ecn-bitw.dll i386$(BKSL)
	cd ..$(BKSL)ws2$(BKSL)lib && $(MAKE) clean
	@echo stamp >windll.stp
	REM --- Windows DLLs built for all architectures

windll.stp:
	$(MAKE) windll

win: windll.stp
	..$(BKSL)test-git.bat /q
	csc $(LIB_BUILD)
	csc $(PROG_BUILD)
	REM or: csc -platform:anycpu32bitpreferred $(PROG_BUILD)
	$(PROG_NAME) $(args)

gnulinux:
	-cd ../ws2/lib && $(MAKE) clean
	cd ../ws2/lib && $(MAKE)
	cp -a ../ws2/lib/libecn-bitw.so ../ws2/lib/libecn-bitw.so.* ./
	-cd ../ws2/lib && $(MAKE) clean
	@echo stamp >gnulnx.stp

gnulnx.stp:
	$(MAKE) gnulinux

dotnet: gnulnx.stp
	$(dotnet_csc) -nologo $(dotnet_cscopts) \
	    -r:Microsoft.Win32.Primitives.dll $(LIB_BUILD)
	$(dotnet_csc) -nologo $(dotnet_cscopts) \
	    -r:Microsoft.Win32.Primitives.dll $(PROG_BUILD)
	echo '{"runtimeOptions":{"framework":{"name":"Microsoft.NETCore.App","version":"$(fwkver)"}}}' \
	    >$(PROG_NAME).runtimeconfig.json
	LD_LIBRARY_PATH=.$${LD_LIBRARY_PATH:+:$$LD_LIBRARY_PATH} \
	    dotnet $(PROG_NAME).exe $(args)

mono: gnulnx.stp
	mono-csc $(LIB_BUILD)
	mono-csc $(PROG_BUILD)
	LD_LIBRARY_PATH=.$${LD_LIBRARY_PATH:+:$$LD_LIBRARY_PATH} \
	    mono $(PROG_NAME).exe $(args)

dotnethelp:
	$(dotnet_csc) -help
