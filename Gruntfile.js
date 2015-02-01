module.exports = function(grunt) {
'use strict';

var moment = require('moment'),
  path = require('path');

var os = (function(){
  var platform = process.platform;
  if (/^win/.test(platform))    { return "windows"; }
  if (/^darwin/.test(platform)) { return "mac"; }
  if (/^linux/.test(platform))  { return "linux"; }
  return null;
})();

//------------------------------------------------------------------------------
// ShellJS
//------------------------------------------------------------------------------

require('shelljs/global');
// shelljs/global makes the following imports:
//   cwd, pwd, ls, find, cp, rm, mv, mkdir, test, cat,
//   str.to, str.toEnd, sed, grep, which, echo,
//   pushd, popd, dirs, ln, exit, env, exec, chmod,
//   tempdir, error

var shellconfig = require('shelljs').config;
shellconfig.silent = true; // hide shell cmd output?
shellconfig.fatal = true;   // stop if cmd failed?

//------------------------------------------------------------------------------
// Grunt Config
//------------------------------------------------------------------------------

var leinJarUrl = 'https://github.com/technomancy/leiningen/releases/download/2.5.0/leiningen-2.5.0-standalone.jar';

grunt.initConfig({

  // LESS conversion
  less: {
    options: {
      compress: true
    },

    default: {
      files: {
        'app/css/main.min.css': 'less/main.less'
      }
    }
  },

  // watch
  watch: {
    options: {
      atBegin: true
    },

    less: {
      files: "less/*.less",
      tasks: "less:default"
    }
  },

  curl: {
    // download leiningen jar
    'app/bin/lein.jar': leinJarUrl
  },

  'download-atom-shell': {
    version: '0.20.5',
    outputDir: 'atom-shell'
  }

});

//------------------------------------------------------------------------------
// Third-party tasks
//------------------------------------------------------------------------------

grunt.loadNpmTasks('grunt-contrib-less');
grunt.loadNpmTasks('grunt-contrib-watch');
grunt.loadNpmTasks('grunt-curl');
grunt.loadNpmTasks('grunt-download-atom-shell');
if (os === "mac") {
  grunt.loadNpmTasks('grunt-appdmg');
}
grunt.loadNpmTasks('winresourcer');

//------------------------------------------------------------------------------
// Setup Tasks
//------------------------------------------------------------------------------

grunt.registerTask('setup', [
  'curl',
  'download-atom-shell',
  'ensure-config-exists',
  'build-lein-profile-tool'
]);

grunt.registerTask('ensure-config-exists', function() {
  pushd("app");
  if (!test("-f", "config.json")) {
    grunt.log.writeln("Creating default config.json...");
    cp("example.config.json", "config.json");
  }
  popd();
});

grunt.registerTask('build-lein-profile-tool', function() {
  pushd("scripts");
  if (!test('-d', "add-lein-profile")) {
    grunt.log.writeln("Cloning...");
    exec("git clone https://github.com/shaunlebron/add-lein-profile.git");
  }
  else {
    pushd("add-lein-profile");

    grunt.log.writeln("Updating...");
    exec("git pull");

    grunt.log.writeln("Cleaning...");
    exec("lein clean");

    grunt.log.writeln("Creating uberjar...");
    exec("lein uberjar");

    grunt.log.writeln("Copying to app/bin...");
    cp("-f", "target/add-lein-profile-*-standalone.jar", "../../app/bin/add-lein-profile.jar");

    popd();
  }
  popd();
});

//------------------------------------------------------------------------------
// Build/Launch Tasks
//------------------------------------------------------------------------------

grunt.registerTask('fresh-cljsbuild', function() {
  grunt.log.writeln("\nCleaning and building ClojureScript files...");
  exec("lein cljsbuild clean");
  exec("lein cljsbuild once");
});

grunt.registerTask('fresh-build', ['less', 'fresh-cljsbuild']);

grunt.registerTask('launch', function() {
  grunt.log.writeln("\nLaunching development version...");
  var exe = {
    windows:  "atom.exe",
    mac:  "Atom.app/Contents/MacOS/Atom",
    linux:  "atom"
  }[os];
  exec(path.join("atom-shell", exe) + " app");
});

//------------------------------------------------------------------------------
// Release
//------------------------------------------------------------------------------

grunt.registerTask('release', function() {

  var build = getBuildMeta();
  var paths = getReleasePaths(build);

  prepRelease(                  build, paths);
  copyAtomAndCuttleToRelease(   build, paths);
  setReleaseConfig(             build, paths);
  installNodeDepsToRelease(     build, paths);
  stampRelease(                 build, paths);
  updateVersionInReadme(        build, paths);

  switch (os) {
    case "mac":     finalizeMacRelease(     build, paths); break;
    case "linux":   finalizeLinuxRelease(   build, paths); break;
    case "windows": finalizeWindowsRelease( build, paths); break;
  }

});

grunt.registerTask('fresh-release', ['fresh-build', 'release']);

//------------------------------------------------------------------------------
// Release - config
//------------------------------------------------------------------------------

var atomShell = {
  windows: {
    exeToRename: "atom.exe",
    renamedExe:  "Cuttle.exe",
    resources:   "resources",
    installExt:  "exe"
  },
  mac: {
    exeToRename: "Atom.app",
    renamedExe:  "Cuttle.app",
    plist:       "Atom.app/Contents/Info.plist",
    resources:   "Atom.app/Contents/Resources",
    installExt:  "dmg"
  },
  linux: {
    exeToRename: "atom",
    renamedExe:  "Cuttle",
    resources:   "resources"
  }
}[os];

function getBuildMeta() {
  grunt.log.writeln("Getting project metadata...");
  var tokens = cat("project.clj").split(" ");
  var build = {
    name:    tokens[1],
    version: tokens[2].replace(/"/g, "").trim(),
    date:    moment().format("YYYY-MM-DD"),
    commit:  exec("git rev-parse HEAD", {silent:true}).output.trim()
  };
  build.releaseName = build.name + "-v" + build.version + "-" + os;
  grunt.log.writeln("name:    "+build.name.cyan);
  grunt.log.writeln("version: "+build.version.cyan);
  grunt.log.writeln("date:    "+build.date.cyan);
  grunt.log.writeln("commit:  "+build.commit.cyan);
  grunt.log.writeln("release: "+build.releaseName.cyan);
  return build;
}

function getReleasePaths(build) {
  var paths = {
    atom: "atom-shell",
    builds: "builds",
    devApp: "app",
    rootPkg: "package.json"
  };
  paths.release = paths.builds + '/' + build.releaseName;
  paths.resources = paths.release + '/' + atomShell.resources;
  paths.install = paths.release + "." + atomShell.installExt;
  paths.releaseApp = paths.resources + "/" + paths.devApp;
  paths.devPkg = paths.devApp + "/package.json";
  paths.releasePkg = paths.releaseApp + "/package.json";
  paths.releaseCfg = paths.releaseApp + "/config.json";
  paths.exeToRename = paths.release + "/" + atomShell.exeToRename;
  paths.renamedExe = paths.release + "/" + atomShell.renamedExe;
  return paths;
}

//------------------------------------------------------------------------------
// Release - subtasks
//------------------------------------------------------------------------------

function prepRelease(build, paths) {
  grunt.log.writeln("\nCleaning previous release...");
  mkdir('-p', paths.builds);
  rm('-rf', paths.install, paths.release);
}

function copyAtomAndCuttleToRelease(build, paths) {
  grunt.log.writeln("\nCopying Atom-Shell and Cuttle to release folder...");
  grunt.log.writeln(paths.atom + " ==> " + paths.release.cyan);
  grunt.log.writeln(paths.devApp + " ==> " + paths.resources.cyan);
  cp('-r', paths.atom+"/", paths.release);
  cp('-r', paths.devApp, paths.resources);
}

function setReleaseConfig(build, paths) {
  grunt.log.writeln("\nRemoving config to force default release settings...");
  rm('-f', paths.releaseCfg);
}

function installNodeDepsToRelease(build, paths) {
  grunt.log.writeln("\nCopying node dependencies to release...");
  cp('-f', paths.rootPkg, paths.releaseApp);
  pushd(paths.releaseApp);
  exec('npm install --production');
  popd();
  cp('-f', paths.devPkg, paths.releaseApp);
}

function stampRelease(build, paths) {
  grunt.log.writeln("\nStamping release with build metadata...");
  var pkg = grunt.file.readJSON(paths.releasePkg);
  pkg["version"] = build.version;
  pkg["build-commit"] = build.commit;
  pkg["build-date"] = build.date;
  JSON.stringify(pkg, null, "  ").to(paths.releasePkg);
}

function updateVersionInReadme(build, paths) {
  grunt.log.writeln("\nUpdating version and download links in readme...");
  sed('-i', /v\d+\.\d+/g, "v"+build.version, "README.md");
}

//------------------------------------------------------------------------------
// Release - finalization
//------------------------------------------------------------------------------

function finalizeMacRelease(build, paths) {

  grunt.log.writeln("\nChanging atom-shell app icon and bundle name to Cuttle's...");
  var plist = __dirname + "/" + paths.release + "/" + atomShell.plist;
  exec("defaults write " + plist + " CFBundleIconFile app/img/cuttle-logo.icns");
  exec("defaults write " + plist + " CFBundleDisplayName Cuttle");
  exec("defaults write " + plist + " CFBundleName Cuttle");
  exec("defaults write " + plist + " CFBundleIdentifier org.cuttle");
  mv(paths.exeToRename, paths.renamedExe);
  var app = paths.renamedExe;

  grunt.log.writeln("\nCreating dmg image...");
  grunt.config.set("appdmg", {
    options: {
      "title": "Cuttle",
      "background": "scripts/dmg/background.png",
      "icon-size": 80,
      "contents": [
        { "x": 448, "y": 344, "type": "link", "path": "/Applications" },
        { "x": 192, "y": 344, "type": "file", "path": app }
      ]
    },
    target: {
      dest: paths.install
    }
  });
  grunt.task.run("appdmg");
}

function finalizeLinuxRelease(build, paths) {
  mv(paths.exeToRename, paths.renamedExe);
}

function finalizeWindowsRelease(build, paths) {
  grunt.log.writeln("\nChanging atom-shell app icon and bundle name to Cuttle's...");
  mv(paths.exeToRename, paths.renamedExe);
  var app = paths.renamedExe;
  grunt.config.set("winresourcer", {
    main: {
      operation: "Update",
      exeFile: app,
      resourceType: "Icongroup",
      resourceName: "1",
      resourceFile: "app/img/cuttle-logo.ico"
    }
  });
  grunt.task.run("winresourcer");

  grunt.config.set("makensis", {
    version:    build.version,
    releaseDir: paths.release,
    outFile:    paths.install
  });
  grunt.task.run("makensis");
}

grunt.registerTask('makensis', function() {
  grunt.log.writeln("\nCreating installer...");
  var config = grunt.config.get("makensis");
  exec(["makensis",
    "/DPRODUCT_VERSION=" + config.version,
    "/DRELEASE_DIR=../" + config.releaseDir,
    "/DOUTFILE=../" + config.outFile,
    "scripts/build-windows-exe.nsi"].join(" "));
});

//------------------------------------------------------------------------------
// Default Task
//------------------------------------------------------------------------------

grunt.registerTask('default', ['watch']);

// end module.exports
};
