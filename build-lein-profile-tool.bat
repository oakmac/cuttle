cd add-lein-profile
lein clean
lein uberjar
copy target\add-lein-profile-*-standalone.jar ..\app\bin\add-lein-profile.jar

echo "Successfully built 'app/bin/add-lein-profile.jar'."
