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
	rm -f *.dll *.exe *.runtimeconfig.json *.so *.so.*

del:
	if exist *.dll (del *.dll)
	if exist *.exe (del *.exe)
	if exist *.lib (del *.lib)
	if exist *.obj (del *.obj)

windll:
	"$(VCINSTALLDIR)$(BKSL)Auxiliary$(BKSL)Build$(BKSL)vcvarsall.bat" amd64 && \
	    $(CC) -DECNBITS_WIN32_DLL $(CPPFLAGS) -DECNBITS_INTERNAL -D_REENTRANT \
	    -D_WIN32_WINNT=0x0600 /O2 $(CFLAGS) /nologo /utf-8 /Wall /c clib.c
	"$(VCINSTALLDIR)$(BKSL)Auxiliary$(BKSL)Build$(BKSL)vcvarsall.bat" amd64 && \
	    $(CC) /O2 $(CFLAGS) /nologo /utf-8 /Wall /LD /FecLib.dll clib.obj \
	    Ws2_32.lib /link /IMPLIB:cLib_imp.lib
	del cLib_imp.exp

win: windll
	csc -t:library unmngd.cs
	csc -r:unmngd.dll exp.cs
	exp

gnulinux:
	$(CC) -D_REENTRANT $(CPPFLAGS) -D_FORTIFY_SOURCE=2 -DECNBITS_INTERNAL \
	    -O2 -g $(CFLAGS) -Wall -Wformat -Wdate-time -Wextra \
	    -fstack-protector-strong -Werror=format-security -fPIC -DPIC -c \
	    -o clib.so clib.c
	rm -f libcLib.so.1 libcLib.so
	$(CC) -O2 -g $(CFLAGS) -Wall -Wformat -Wdate-time -Wextra \
	    -fstack-protector-strong -Werror=format-security -fPIC \
	    -Wl,--no-undefined $(LDFLAGS) -Wl,-z,relro -Wl,-z,now \
	    -Wl,--as-needed -shared clib.so \
	    -Wl,-soname,libcLib.so.1 -o libcLib.so.1.0
	ln -s libcLib.so.1.0 libcLib.so.1
	ln -s libcLib.so.1.0 libcLib.so

dotnet: gnulinux
	$(dotnet_csc) -nologo $(dotnet_cscopts) \
	    -r:Microsoft.Win32.Primitives.dll -t:library unmngd.cs
	$(dotnet_csc) -nologo $(dotnet_cscopts) \
	    -r:Microsoft.Win32.Primitives.dll -r:unmngd.dll exp.cs
	echo '{"runtimeOptions":{"framework":{"name":"Microsoft.NETCore.App","version":"$(fwkver)"}}}' \
	    >exp.runtimeconfig.json
	LD_LIBRARY_PATH=.$${LD_LIBRARY_PATH:+:$$LD_LIBRARY_PATH} \
	    dotnet exp.exe

mono: gnulinux
	mono-csc -t:library unmngd.cs
	mono-csc -r:unmngd.dll exp.cs
	LD_LIBRARY_PATH=.$${LD_LIBRARY_PATH:+:$$LD_LIBRARY_PATH} \
	    mono exp.exe

dotnethelp:
	$(dotnet_csc) -help
