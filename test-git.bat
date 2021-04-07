@echo off
set v=
set /p v=<.symlink.tst
if "%v%"=="meow" (goto ok)
goto nope
:ok
set v=
if "%1"=="/q" (exit /b 0)
echo.
echo This git repository seems to have been cloned with symlinks enabled, ok.
exit /b 0
:nope
set v=
echo.
echo ==================================================
echo THIS GIT REPOSITORY IS *NOT* CHECKED OUT CORRECTLY
echo ==================================================
echo.
echo It MUST be cloned with symbolic links ENABLED.
echo.
echo Check https://stackoverflow.com/a/49913019/2171120 for
echo information on how to do that (including using gpedit
echo as https://www.joshkel.com/2018/01/18/symlinks-in-windows/
echo describes, or setting Windows 10 (1703 or newer) to
echo Developer Mode). Then, erase this directory and clone
echo it again (then run this script again to verify it's right).
echo.
exit /b 1
