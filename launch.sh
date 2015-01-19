#!/bin/bash

# Launch Cuttle using our local Atom Shell installation
# (Mac, Linux, or Cygwin)

# from: http://stackoverflow.com/a/17072017/142317
if [ "$(uname)" == "Darwin" ]; then
  ATOM="atom-shell/Atom.app/Contents/MacOS/Atom"
elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
  ATOM="atom-shell/atom"
elif [ "$(expr substr $(uname -s) 1 10)" == "MINGW32_NT" ]; then
  ATOM="atom-shell/atom.exe"
else
  echo "Cannot detect a supported OS."
  exit 1
fi

$ATOM app
