@echo off

pushd "%~dp0\.."

echo. & echo Creating default config file...
if not exist app\config.json copy app\example.config.json app\config.json

echo. & echo Installing node dependencies...
call npm install

echo. & echo Installing grunt...
call npm install -g grunt-cli

echo. & echo Installing winresourcer...
call npm install -g winresourcer

echo. & echo Installing Leiningen jar...
call grunt curl

echo. & echo Installing Atom Shell...
call grunt download-atom-shell

echo. & echo Building lein profile tool...
call "scripts\build-lein-profile-tool.bat"

echo. & echo Cuttle setup complete.

popd
