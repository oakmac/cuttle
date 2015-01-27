#!/bin/sh

set -e

cd "`dirname $0`"

if [ ! -d "add-lein-profile" ]; then
  git clone https://github.com/shaunlebron/add-lein-profile.git
fi

cd "add-lein-profile"

git pull

lein clean
lein uberjar
cp target/add-lein-profile-*-standalone.jar ../../app/bin/add-lein-profile.jar

echo "Successfully built 'app/bin/add-lein-profile.jar'."
