#!/bin/bash

# exit on errors
set -e

cd "`dirname $0`/.."

echo; echo "Installing node dependencies..."
npm install

echo; echo "Installing grunt..."
sudo npm install -g grunt-cli

grunt setup

echo; echo "Cuttle setup complete."
