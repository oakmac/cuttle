@echo off

pushd "%~dp0\.."

echo. & echo Installing node dependencies...
rem "grunt-appdmg" is a mac-only dependency that will fail to build on windows.
rem     So we are including it as an optionalDependency in package.json
rem     and preventing its installation with npm's --no-optional flag.
call npm install

echo. & echo Installing grunt...
call npm install -g grunt-cli

call grunt setup

echo. & echo Cuttle setup complete.

popd
