# GNU make and nmake compatible, except clean

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
	@echo Windows: make win, make del
	@echo Linux: make dotnet, make mono, make clean

clean:
	rm -f *.exe *.runtimeconfig.json

del:
	if exist *.exe (del *.exe)

win:
	csc exp.cs
	exp

dotnet:
	$(dotnet_csc) -nologo $(dotnet_cscopts) \
	    -r:Microsoft.Win32.Primitives.dll exp.cs
	echo '{"runtimeOptions":{"framework":{"name":"Microsoft.NETCore.App","version":"$(fwkver)"}}}' \
	    >exp.runtimeconfig.json
	dotnet exp.exe

mono:
	mono-csc exp.cs
	mono exp.exe

dotnethelp:
	$(dotnet_csc) -help
