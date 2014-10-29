@echo off

rem Get full path to the directory of this script.
set DIR=%~dp0

rem Set env var to make lein use our local lein jar.
set LEIN_JAR=%DIR%\lein.jar

rem Run our local lein and pass all arguments to it.
%DIR%\lein.bat %*
