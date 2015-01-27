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

# copy node_modules
mkdir $RELEASE_RSRC/app/node_modules
cp -R node_modules/fs.extra $RELEASE_RSRC/app/node_modules/fs.extra
cp -R node_modules/open $RELEASE_RSRC/app/node_modules/open

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

  mv $RELEASE_DIR/Atom.app $RELEASE_DIR/Cuttle.app

elif [ "$OS" == "linux" ]; then

  mv $RELEASE_DIR/atom $RELEASE_DIR/Cuttle

elif [ "$OS" == "windows" ]; then

  winresourcer --operation=Update \
               --exeFile=$RELEASE_DIR/atom.exe \
               --resourceType=Icongroup \
               --resourceName:1 \
               --resourceFile:$RELEASE_RSRC/app/img/cuttle-logo.ico

  mv $RELEASE_DIR/atom.exe $RELEASE_DIR/Cuttle.exe

  makensis scripts/build-windows-exe.nsi
  mv scripts/$RELEASE.exe $BUILDS/$RELEASE.exe

fi

#----------------------------------------------------------------------
# Create zip
#----------------------------------------------------------------------

if [ "$1" == "-z" ]; then
  zip -r $RELEASE_ZIP $RELEASE_DIR
fi
