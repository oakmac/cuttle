#!/bin/bash

# Create cljsbuild-ui release using our local Atom Shell installation
# (Mac, Linux, or Cygwin)

#----------------------------------------------------------------------
# Get OS-specific Atom details
#----------------------------------------------------------------------

ATOM_DIR="atom-shell"

# from: http://stackoverflow.com/a/17072017/142317
if [ "$(uname)" == "Darwin" ]; then
  OS="mac"
  EXE="Atom.app/Contents/MacOS/Atom"
  PLIST="Atom.app/Contents/Info.plist"
  RESOURCES="Atom.app/Contents/Resources"

elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
  OS="linux"
  EXE="atom"
  RESOURCES="resources"

elif [ "$(expr substr $(uname -s) 1 10)" == "MINGW32_NT" ]; then
  OS="windows"
  EXE="atom.exe"
  RESOURCES="resources"

else
  echo "Cannot detect a supported OS."
  exit 1
fi

#----------------------------------------------------------------------
# Determine release name and output location
#----------------------------------------------------------------------

META=`head -n 1 project.clj`
NAME=`echo $META | cut -d' ' -f2`
VERSION=`echo $META | cut -d' ' -f3 | tr -d '"'`

BUILDS=builds
mkdir -p $BUILDS

RELEASE="$NAME-$VERSION-$OS"
RELEASE_DIR="$BUILDS/$RELEASE"
RELEASE_ZIP="$BUILDS/${RELEASE}.zip"
RELEASE_RSRC="$RELEASE_DIR/$RESOURCES"

rm -rf $RELEASE_DIR $RELEASE_ZIP

#----------------------------------------------------------------------
# Copy Atom installation and app directory into output location
#----------------------------------------------------------------------

echo "Creating $RELEASE_DIR ..."

cp -R $ATOM_DIR $RELEASE_DIR
cp -R app $RELEASE_RSRC

#----------------------------------------------------------------------
# Disable Dev-Tools
#----------------------------------------------------------------------

CONFIG=$RELEASE_RSRC/app/config.json
if [ -f $CONFIG ]; then
  sed -i .bak 's/"dev-tools"[[:space:]]*:[[:space:]]*true/"dev-tools": false/' $CONFIG
fi

#----------------------------------------------------------------------
# Polishing
#----------------------------------------------------------------------

if [ "$OS" == "mac" ]; then

  FULL_PLIST="$(pwd)/$RELEASE_DIR/$PLIST"

  defaults write $FULL_PLIST CFBundleIconFile 'app/img/clojure-logo.icns'
  defaults write $FULL_PLIST CFBundleDisplayName 'CLJS-UI'
  defaults write $FULL_PLIST CFBundleName 'CLJS-UI'

  mv $RELEASE_DIR/Atom.app $RELEASE_DIR/ClojureScript.app

#elif [ "$OS" == "linux" ]; then
#elif [ "$OS" == "windows" ]; then
fi

#----------------------------------------------------------------------
# Create zip
#----------------------------------------------------------------------

if [ "$1" == "-z" ]; then
  zip -r $RELEASE_ZIP $RELEASE_DIR
fi
