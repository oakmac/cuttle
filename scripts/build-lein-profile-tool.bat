@echo off

pushd "%~dp0\..\add-lein-profile"

call lein clean
call lein uberjar
copy target\add-lein-profile-*-standalone.jar ..\app\bin\add-lein-profile.jar

popd

echo Successfully built 'app/bin/add-lein-profile.jar'.
