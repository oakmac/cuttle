#!/bin/bash

# This kills all Cuttle leiningen processes
# We need this on Mac currently since "stop" is not killing them yet.

ps -ef | grep -v grep | grep "with-profile +cuttle" | cut -d" " -f4 | xargs kill
