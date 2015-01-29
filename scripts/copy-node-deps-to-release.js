#!/usr/bin/env node

var args = process.argv;
args.splice(0,2);

var SRC_FILE = args[0];
var DST_FILE = args[1];

var fs = require("fs");

var src_content = fs.readFileSync(SRC_FILE);
var src_data = JSON.parse(src_content);

var dst_content = fs.readFileSync(DST_FILE);
var dst_data = JSON.parse(dst_content);

// have to do this so npm install works without complaining
// about my version not being semver-compliant
delete dst_data["version"];

dst_data["dependencies"] = src_data["dependencies"]

var new_dst_content = JSON.stringify(dst_data, null, "  ");
fs.writeFileSync(DST_FILE, new_dst_content);
