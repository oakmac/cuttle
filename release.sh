#!/bin/bash

# Create Cuttle release using our local Atom Shell installation
# (Mac, Linux, or Cygwin)

# NOTE:
# for Windows you will need "makensis" on your PATH
# http://nsis.sourceforge.net/Main_Page
# you also may need ConEmu for bash
# https://code.google.com/p/conemu-maximus5/

set -e

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
  INSTALL_EXT="dmg"

elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
  OS="linux"
  EXE="atom"
  RESOURCES="resources"

elif [ "$(expr substr $(uname -s) 1 10)" == "MINGW32_NT" ]; then
  OS="windows"
  EXE="atom.exe"
  RESOURCES="resources"
  INSTALL_EXT="exe"

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
BUILD_COMMIT=`git rev-parse HEAD`
BUILD_DATE=`date +'%F'`

BUILDS=builds
mkdir -p $BUILDS

RELEASE="$NAME-v$VERSION-$OS"
RELEASE_DIR="$BUILDS/$RELEASE"
RELEASE_ZIP="$BUILDS/${RELEASE}.zip"
RELEASE_RSRC="$RELEASE_DIR/$RESOURCES"

RELEASE_INSTALL="$BUILDS/$RELEASE.$INSTALL_EXT"

rm -rf $RELEASE_DIR $RELEASE_ZIP $RELEASE_INSTALL

#----------------------------------------------------------------------
# Copy Atom installation and app directory into output location
#----------------------------------------------------------------------

echo "Creating $RELEASE_DIR ..."

cp -R $ATOM_DIR $RELEASE_DIR
cp -R app $RELEASE_RSRC

# don't copy our development log file to release
rm -f $RELEASE_RSRC/app/cuttle.log

# We are storing production and development dependencies in Node's package.json.
# Production dependencies must be copied to the release folder, so we do this
# by swapping out the Atom Shell package.json for Node's package.json, then
# `npm install` helps us copy the correct dependencies over.
cp package.json $RELEASE_RSRC/app
pushd $RELEASE_RSRC/app
npm install --production
popd
cp app/package.json $RELEASE_RSRC/app/

# write build version, timestamp, and commit hash
json -I -f $RELEASE_RSRC/app/package.json \
  -e "this[\"version\"]=\"$VERSION\"" \
  -e "this[\"build-commit\"]=\"$BUILD_COMMIT\"" \
  -e "this[\"build-date\"]=\"$BUILD_DATE\""

#----------------------------------------------------------------------
# Disable Dev-Tools
#----------------------------------------------------------------------

CONFIG=$RELEASE_RSRC/app/config.json
if [ -f $CONFIG ]; then
  rm $CONFIG
fi

#----------------------------------------------------------------------
# Polishing
#----------------------------------------------------------------------

if [ "$OS" == "mac" ]; then

  FULL_PLIST="$(pwd)/$RELEASE_DIR/$PLIST"

  defaults write $FULL_PLIST CFBundleIconFile 'app/img/cuttle-logo.icns'
  defaults write $FULL_PLIST CFBundleDisplayName 'Cuttle'
  defaults write $FULL_PLIST CFBundleName 'Cuttle'
  defaults write $FULL_PLIST CFBundleIdentifier 'org.cuttle'

  FINAL_APP=$BUILDS/latest/Cuttle.app # appdmg.json uses this path to find the app

  rm -rf $FINAL_APP
  mv $RELEASE_DIR/Atom.app $FINAL_APP

  appdmg scripts/dmg/appdmg.json $RELEASE_INSTALL


elif [ "$OS" == "linux" ]; then

  mv $RELEASE_DIR/atom $RELEASE_DIR/Cuttle

elif [ "$OS" == "windows" ]; then

  winresourcer --operation=Update \
               --exeFile=$RELEASE_DIR/atom.exe \
               --resourceType=Icongroup \
               --resourceName:1 \
               --resourceFile:$RELEASE_RSRC/app/img/cuttle-logo.ico

  mv $RELEASE_DIR/atom.exe $RELEASE_DIR/Cuttle.exe

  NSI_FILE=scripts/build-windows-exe.nsi

  makensis \
    //DPRODUCT_VERSION=$VERSION \
    $NSI_FILE

  mv scripts/$RELEASE.exe $RELEASE_INSTALL

fi

#----------------------------------------------------------------------
# Create zip
#----------------------------------------------------------------------

if [ "$1" == "-z" ]; then
  zip -r $RELEASE_ZIP $RELEASE_DIR
fi
