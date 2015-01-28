#!/bin/sh

# exit on errors
set -e

cd "`dirname $0`/.."

echo; echo "Creating default config file..."
if [ ! -f "app/config.json" ]; then
  cp app/example.config.json app/config.json
fi

echo; echo "Installing node dependencies..."
npm install

echo; echo "Installing grunt..."
sudo npm install -g grunt-cli

echo; echo "Installing app dmg creator..."
sudo npm install -g appdmg

echo; echo "Installing json command line tool..."
sudo npm install -g json

echo; echo "Installing winresourcer..."
sudo npm install -g winresourcer

echo; echo "Installing Leiningen jar..."
grunt curl

echo; echo "Installing Atom Shell..."
grunt download-atom-shell

echo; echo "Building lein profile tool..."
scripts/build-lein-profile-tool.sh

echo; echo "Cuttle setup complete."
