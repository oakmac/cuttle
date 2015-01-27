@echo off

pushd "%~dp0"

if not exist add-lein-profile git clone https://github.com/shaunlebron/add-lein-profile.git

cd add-lein-profile
git pull

call lein clean
call lein uberjar
copy target\add-lein-profile-*-standalone.jar ..\..\app\bin\add-lein-profile.jar

popd

echo Successfully built 'app/bin/add-lein-profile.jar'.
