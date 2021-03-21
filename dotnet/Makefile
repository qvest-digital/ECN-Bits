# GNU make and nmake compatible, except clean

# sdkver=$(dotnet --version)
sdkver=5.0.201
# fwkver=$(dotnet --list-runtimes | sed --posix -n '/^Microsoft.NETCore.App \([^ ]*\) .*$/{s//\1/p;q;}')
fwkver=5.0.4

# dotnet-sdk-5.0 installed via .deb package
dotnethome=/usr/share/dotnet
dotnetlib=$(dotnethome)/shared/Microsoft.NETCore.App/$(fwkver)

all:
	@echo choose one: make win, make dotnet, make mono

clean:
	rm -f *.exe *.runtimeconfig.json

win:
	csc exp.cs
	exp

dotnet:
	dotnet $(dotnethome)/sdk/$(sdkver)/Roslyn/bincore/csc.dll -nologo \
	    -lib:$(dotnetlib) -r:netstandard.dll -r:Microsoft.CSharp.dll \
	    -r:System.dll $$(cd $(dotnetlib) && for x in System.*.dll; do \
		echo -r:$$x; done) \
	    -r:Microsoft.Win32.Primitives.dll exp.cs
	echo '{"runtimeOptions":{"framework":{"name":"Microsoft.NETCore.App","version":"$(fwkver)"}}}' \
	    >exp.runtimeconfig.json
	dotnet exp.exe

mono:
	mono-csc exp.cs
	mono exp.exe

dotnethelp:
	dotnet $(dotnethome)/sdk/$(sdkver)/Roslyn/bincore/csc.dll -help
