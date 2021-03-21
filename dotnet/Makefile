# GNU make and nmake compatible, except clean

all:
	@echo choose one: make win, make dotnet, make mono

clean:
	rm -f *.exe

win:
	csc exp.cs
	exp

dotnet:
	dotnet build exp.csproj
	dotnet exp.exe

mono:
	mono-csc exp.cs
	mono exp.exe
