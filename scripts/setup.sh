#!/bin/bash

# exit on errors
set -e

cd "`dirname $0`/.."

echo; echo "Installing node dependencies..."

if [[ "$OSTYPE" == "darwin"* ]]; then
  npm install
else
  # "grunt-appdmg" is a mac-only dependency that will fail to build on linux.
  # So we are including it as an optionalDependency in package.json
  # and preventing its installation with npm's --no-optional flag.
  npm install --no-optional
fi

echo; echo "Installing grunt..."
sudo npm install -g grunt-cli

grunt setup

echo; echo "Cuttle setup complete."
