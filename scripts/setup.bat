@echo off

pushd "%~dp0\.."

echo. & echo Installing node dependencies...
call npm install

echo. & echo Installing grunt...
call npm install -g grunt-cli

echo. & echo Cuttle setup complete.

popd
