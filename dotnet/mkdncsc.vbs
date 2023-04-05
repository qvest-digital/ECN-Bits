' -*- mode: vbscript -*-
'-
' Copyright © 2023
'	mirabilos <t.glaser@tarent.de>
' Licensor: Deutsche Telekom
'
' Provided that these terms and disclaimer and all copyright notices
' are retained or reproduced in an accompanying document, permission
' is granted to deal in this work without restriction, including un‐
' limited rights to use, publicly perform, distribute, sell, modify,
' merge, give away, or sublicence.
'
' This work is provided “AS IS” and WITHOUT WARRANTY of any kind, to
' the utmost extent permitted by applicable law, neither express nor
' implied; without malicious intent or gross negligence. In no event
' may a licensor, author or contributor be held liable for indirect,
' direct, other damage, loss, or other issues arising in any way out
' of dealing in the work, even if advised of the possibility of such
' damage or existence of a defect, except proven that it results out
' of said person’s immediate fault when using the work as intended.

on error resume next
wscript.echo "mkdncsc.vbs starting"

function chktoint(ival, name)
	nval = CInt(ival)
	sval = CStr(nval)
	if ival <> sval then
		wscript.echo "Cannot convert " + name + " to integer: " + ival
		wscript.quit 1
	end if
	chktoint = nval
end function

state = 0
basepath = ""
netcoremaj = 0
netcoremin = 0
netcoreplv = 0
netcoredir = ""
netcorehad = 0

set bpre = new regexp
bpre.pattern = "^ Base Path: *([A-Z]:\\.*\\)$"
set ncre = new regexp
ncre.pattern = "^  Microsoft\.NETCore\.App ([0-9]+)\.([0-9]+)\.([0-9]+) \[([A-Z]:\\.*)]$"

if err.number <> 0 then
	wscript.echo "init error"
	wscript.quit 255
end if

do while not wscript.stdin.AtEndOfStream
	s = wscript.stdin.ReadLine
	select case state
	case 0
		if s = "Runtime Environment:" then state = 1
	case 1
		set matches = bpre.execute(s)
		if matches.count = 1 then
			basepath = matches(0).submatches(0)
			state = 2
		elseif s = "" then
			state = 999
		end if
	case 2
		if s = ".NET runtimes installed:" then state = 3
	case 3
		set matches = ncre.execute(s)
		if matches.count = 1 then
			maj = chktoint(matches(0).submatches(0), "major")
			min = chktoint(matches(0).submatches(1), "minor")
			plv = chktoint(matches(0).submatches(2), "patchlevel")
			if maj > netcoremaj then
				useit = 1
			elseif maj < netcoremaj then
				useit = 0
			elseif min > netcoremin then
				useit = 1
			elseif min < netcoremin then
				useit = 0
			elseif plv > netcoreplv then
				useit = 1
			else
				useit = 0
			end if
			if useit = 1 then
				netcoremaj = maj
				netcoremin = min
				netcoreplv = plv
				netcoredir = matches(0).submatches(3)
				netcorehad = 1
			end if
		elseif s = "" then
			state = 4 + netcorehad
		end if
	case else
		rem nothing to do here
	end select
loop

if err.number <> 0 then
	wscript.echo "stdin read error"
	wscript.quit 255
end if

if state <> 5 then
	wscript.echo "Cannot find necessary info: state = " + state
	wscript.quit 1
end if

if basepath = "" then
	wscript.echo "Cannot find basepath"
	wscript.quit 1
end if

netcorever = CStr(netcoremaj) + "." + CStr(netcoremin) + "." + CStr(netcoreplv)
netcoredir = netcoredir + "\" + netcorever

if netcoredir = "" then
	wscript.echo "Cannot find netcoredir for " + netcorever
	wscript.quit 1
end if

if netcoremaj < 5 then
	wscript.echo ".NET too old, only " + netcorever + " and older found"
	wscript.quit 1
end if

set fso = CreateObject("Scripting.FileSystemObject")
set of = fso.CreateTextFile("dnmkrtcf.ba_", true)
of.WriteLine "@echo off"
of.WriteLine "echo {""runtimeOptions"":{""framework"":{""name"":""Microsoft.NETCore.App"",""version"":""" + netcorever + """}}}"
of.Close

if err.number <> 0 then
	wscript.echo "error writing dnmkrtcf.ba_"
	wscript.quit 255
end if

set of = fso.CreateTextFile("dncsc.ba_", true)
of.WriteLine "@echo off"
of.WriteLine "setlocal"
of.WriteLine "pushd " + netcoredir
of.WriteLine "set lf="
of.WriteLine "setlocal EnableDelayedExpansion"
of.WriteLine "for %%f in (System.*.dll) do set lf=!lf! -r:%%f"
of.WriteLine "setlocal DisableDelayedExpansion"
of.WriteLine "popd"
of.WriteLine "dotnet """ + basepath + "Roslyn\bincore\csc.dll"" -lib:""" + netcoredir + """ -r:netstandard.dll -r:Microsoft.CSharp.dll -r:System.dll %lf% -r:Microsoft.Win32.Primitives.dll %*"
of.WriteLine "exit /b %ERRORLEVEL%"
of.Close

if err.number <> 0 then
	wscript.echo "error writing dncsc.ba_"
	wscript.quit 255
end if

wscript.echo "mkdncsc.vbs finished"
wscript.quit 0
