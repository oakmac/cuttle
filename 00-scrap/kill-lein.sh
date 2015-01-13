#!/bin/bash

# This kills all leiningen processes running cljsbuild.
# We need this on Mac currently since "stop" is not killing them yet.

ps -ef | grep -v grep | grep "leiningen.core.main cljsbuild auto" | cut -d" " -f4 | xargs kill
