#!/bin/bash

# This kills all cljsbuild-ui leiningen processes
# We need this on Mac currently since "stop" is not killing them yet.

ps -ef | grep -v grep | grep "with-profile +cljsbuild-ui"
