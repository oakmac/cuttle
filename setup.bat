
echo "Installing node dependencies..."
npm install

echo "Installing grunt..."
sudo npm install -g grunt-cli

echo "Installing winresourcer..."
sudo npm install -g winresourcer

echo "Installing Leiningen jar..."
grunt curl

echo "Installating Atom Shell..."
grunt download-atom-shell

echo "Building lein profile tool..."
build-lein-profile-tool.bat

echo "Setup complete."
