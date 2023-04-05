@echo off
setlocal
pushd C:\Program Files\dotnet\shared\Microsoft.NETCore.App\5.0.12
set lf=
setlocal EnableDelayedExpansion
for %%f in (System.*.dll) do set lf=!lf! -r:%%f
setlocal DisableDelayedExpansion
popd
dotnet "C:\Program Files\dotnet\sdk\5.0.403\Roslyn\bincore\csc.dll" -lib:"C:\Program Files\dotnet\shared\Microsoft.NETCore.App\5.0.12" -r:netstandard.dll -r:Microsoft.CSharp.dll -r:System.dll %lf% %*
exit /b %ERRORLEVEL%
