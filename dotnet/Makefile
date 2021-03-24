# GNU make and nmake compatible, except clean
BKSL=^\

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

all:
	@echo choose one:
	@echo Windows: nmake win, nmake del
	@echo Linux: make dotnet, make mono, make clean

clean:
	rm -rf armhf arm64 amd64 i386
	rm -f *.dll *.exe *.runtimeconfig.json *.so *.so.*

del:
	-rd /s /q armhf arm64 amd64 i386 2>nul
	if exist *.dll (del *.dll)
	if exist *.exe (del *.exe)
	if exist *.lib (del *.lib)
	if exist *.obj (del *.obj)

windll:
	-rd /s /q armhf arm64 amd64 i386 2>nul
	md armhf
	"$(VCINSTALLDIR)$(BKSL)Auxiliary$(BKSL)Build$(BKSL)vcvarsall.bat" x86_arm && \
	    $(CC) -DECNBITS_WIN32_DLL $(CPPFLAGS) -DECNBITS_INTERNAL -D_REENTRANT \
	    -D_WIN32_WINNT=0x0600 /O2 $(CFLAGS) /nologo /utf-8 /Wall /c \
	    clib.c hll_mono.c
	"$(VCINSTALLDIR)$(BKSL)Auxiliary$(BKSL)Build$(BKSL)vcvarsall.bat" x86_arm && \
	    $(CC) /O2 $(CFLAGS) /nologo /utf-8 /Wall /LD /Fearmhf$(BKSL)cLib.dll \
	    clib.obj hll_mono.obj Ws2_32.lib /link /IMPLIB:armhf$(BKSL)cLib_imp.lib
	del *.obj
	md arm64
	"$(VCINSTALLDIR)$(BKSL)Auxiliary$(BKSL)Build$(BKSL)vcvarsall.bat" x86_arm64 && \
	    $(CC) -DECNBITS_WIN32_DLL $(CPPFLAGS) -DECNBITS_INTERNAL -D_REENTRANT \
	    -D_WIN32_WINNT=0x0600 /O2 $(CFLAGS) /nologo /utf-8 /Wall /c \
	    clib.c hll_mono.c
	"$(VCINSTALLDIR)$(BKSL)Auxiliary$(BKSL)Build$(BKSL)vcvarsall.bat" x86_arm64 && \
	    $(CC) /O2 $(CFLAGS) /nologo /utf-8 /Wall /LD /Fearm64$(BKSL)cLib.dll \
	    clib.obj hll_mono.obj Ws2_32.lib /link /IMPLIB:arm64$(BKSL)cLib_imp.lib
	del *.obj
	md amd64
	"$(VCINSTALLDIR)$(BKSL)Auxiliary$(BKSL)Build$(BKSL)vcvarsall.bat" x86_amd64 && \
	    $(CC) -DECNBITS_WIN32_DLL $(CPPFLAGS) -DECNBITS_INTERNAL -D_REENTRANT \
	    -D_WIN32_WINNT=0x0600 /O2 $(CFLAGS) /nologo /utf-8 /Wall /c \
	    clib.c hll_mono.c
	"$(VCINSTALLDIR)$(BKSL)Auxiliary$(BKSL)Build$(BKSL)vcvarsall.bat" x86_amd64 && \
	    $(CC) /O2 $(CFLAGS) /nologo /utf-8 /Wall /LD /Feamd64$(BKSL)cLib.dll \
	    clib.obj hll_mono.obj Ws2_32.lib /link /IMPLIB:amd64$(BKSL)cLib_imp.lib
	del *.obj
	md i386
	"$(VCINSTALLDIR)$(BKSL)Auxiliary$(BKSL)Build$(BKSL)vcvarsall.bat" x86 && \
	    $(CC) -DECNBITS_WIN32_DLL $(CPPFLAGS) -DECNBITS_INTERNAL -D_REENTRANT \
	    -D_WIN32_WINNT=0x0600 /O2 $(CFLAGS) /nologo /utf-8 /Wall /c \
	    clib.c hll_mono.c
	"$(VCINSTALLDIR)$(BKSL)Auxiliary$(BKSL)Build$(BKSL)vcvarsall.bat" x86 && \
	    $(CC) /O2 $(CFLAGS) /nologo /utf-8 /Wall /LD /Fei386$(BKSL)cLib.dll \
	    clib.obj hll_mono.obj Ws2_32.lib /link /IMPLIB:i386$(BKSL)cLib_imp.lib
	del *.obj

win: windll
	csc -t:library unmngd.cs monosupp.cs
	csc -r:unmngd.dll exp.cs
	exp

gnulinux:
	$(CC) -D_REENTRANT $(CPPFLAGS) -D_FORTIFY_SOURCE=2 -DECNBITS_INTERNAL \
	    -O2 -g $(CFLAGS) -Wall -Wformat -Wdate-time -Wextra \
	    -fstack-protector-strong -Werror=format-security -fPIC -DPIC -c \
	    -o clib.so clib.c
	$(CC) -D_REENTRANT $(CPPFLAGS) -D_FORTIFY_SOURCE=2 -DECNBITS_INTERNAL \
	    -O2 -g $(CFLAGS) -Wall -Wformat -Wdate-time -Wextra \
	    -fstack-protector-strong -Werror=format-security -fPIC -DPIC -c \
	    -o hll_mono.so hll_mono.c
	rm -f libcLib.so.1 libcLib.so
	$(CC) -O2 -g $(CFLAGS) -Wall -Wformat -Wdate-time -Wextra \
	    -fstack-protector-strong -Werror=format-security -fPIC \
	    -Wl,--no-undefined $(LDFLAGS) -Wl,-z,relro -Wl,-z,now \
	    -Wl,--as-needed -shared clib.so hll_mono.so \
	    -Wl,-soname,libcLib.so.1 -o libcLib.so.1.0
	ln -s libcLib.so.1.0 libcLib.so.1
	ln -s libcLib.so.1.0 libcLib.so

dotnet: gnulinux
	$(dotnet_csc) -nologo $(dotnet_cscopts) \
	    -r:Microsoft.Win32.Primitives.dll -t:library unmngd.cs monosupp.cs
	$(dotnet_csc) -nologo $(dotnet_cscopts) \
	    -r:Microsoft.Win32.Primitives.dll -r:unmngd.dll exp.cs
	echo '{"runtimeOptions":{"framework":{"name":"Microsoft.NETCore.App","version":"$(fwkver)"}}}' \
	    >exp.runtimeconfig.json
	LD_LIBRARY_PATH=.$${LD_LIBRARY_PATH:+:$$LD_LIBRARY_PATH} \
	    dotnet exp.exe

mono: gnulinux
	mono-csc -t:library unmngd.cs monosupp.cs
	mono-csc -r:unmngd.dll exp.cs
	LD_LIBRARY_PATH=.$${LD_LIBRARY_PATH:+:$$LD_LIBRARY_PATH} \
	    mono exp.exe

dotnethelp:
	$(dotnet_csc) -help
