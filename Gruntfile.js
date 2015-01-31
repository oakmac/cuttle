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
shellconfig.silent = false; // hide shell cmd output?
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

grunt.registerTask('ensure-config-exists', function() {
  pushd("app");
  if (!test("-f", "config.json")) {
    cp("example.config.json", "config.json");
  }
  popd();
});

grunt.registerTask('build-lein-profile-tool', function() {
  pushd("scripts");
  if (!test('-d', "add-lein-profile")) {
    exec("git clone https://github.com/shaunlebron/add-lein-profile.git");
  }
  else {
    pushd("add-lein-profile");
    exec("git pull");
    exec("lein clean");
    exec("lein uberjar");
    cp("-f", "target/add-lein-profile-*-standalone.jar", "../../app/bin/add-lein-profile.jar");
    popd();
  }
  popd();
});

grunt.registerTask('setup', [
  'curl',
  'download-atom-shell',
  'ensure-config-exists',
  'build-lein-profile-tool'
]);

//------------------------------------------------------------------------------
// Build/Release Tasks
//------------------------------------------------------------------------------

var atomShell = {
  windows: {
    cmdlineExe:  "atom.exe",
    exeToRename: "atom.exe",
    renamedExe:  "Cuttle.exe",
    resources:   "resources",
    installExt:  "exe"
  },
  mac: {
    cmdlineExe:  "Atom.app/Contents/MacOS/Atom",
    exeToRename: "Atom.app",
    renamedExe:  "Cuttle.app",
    plist:       "Atom.app/Contents/Info.plist",
    resources:   "Atom.app/Contents/Resources",
    installExt:  "dmg"
  },
  linux: {
    cmdlineExe:  "atom",
    exeToRename: "atom",
    renamedExe:  "Cuttle",
    resources:   "resources"
  }
}[os];

grunt.registerTask('fresh-build', function() {
  exec("lein cljsbuild clean");
  exec("lein cljsbuild once");
  grunt.task.run("less");
});

grunt.registerTask('launch', function() {
  exec(path.join("atom-shell", atomShell.cmdlineExe) + " app");
});

grunt.registerTask('release', function() {

  // setup build metadata
  var tokens = cat("project.clj").split(" ");
  var build = {
    name:    tokens[1],
    version: tokens[2].replace(/"/g, "").trim(),
    date:    moment().format("YYYY-MM-DD"),
    commit:  exec("git rev-parse HEAD", {silent:true}).output.trim()
  };
  build.releaseName = build.name + "-v" + build.version + "-" + os;

  // determine build paths
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

  mkdir('-p', paths.builds);
  rm('-rf', paths.install, paths.release);

  cp('-r', paths.atom+"/", paths.release);
  cp('-r', paths.devApp, paths.resources);

  rm('-f', paths.releaseApp + "/config.json");

  cp('-f', paths.rootPkg, paths.releaseApp);
  pushd(paths.releaseApp);
  exec('npm install --production');
  popd();
  cp('-f', paths.devPkg, paths.releaseApp);

  var pkg = grunt.file.readJSON(paths.releasePkg);
  pkg["version"] = build.version;
  pkg["build-commit"] = build.commit;
  pkg["build-date"] = build.date;
  JSON.stringify(pkg, null, "  ").to(paths.releasePkg);

  rm('-f', paths.releaseCfg);

  switch (os) {
    case "mac":     finalizeMacRelease(build, paths); break;
    case "linux":   finalizeLinuxRelease(build, paths); break;
    case "windows": finalizeWindowsRelease(build, paths); break;
  }

});

function finalizeMacRelease(build, paths) {
  var plist = __dirname + "/" + paths.release + "/" + atomShell.plist;
  grunt.log.writeln(plist);

  exec("defaults write " + plist + " CFBundleIconFile app/img/cuttle-logo.icns");
  exec("defaults write " + plist + " CFBundleDisplayName Cuttle");
  exec("defaults write " + plist + " CFBundleName Cuttle");
  exec("defaults write " + plist + " CFBundleIdentifier org.cuttle");

  mv(paths.exeToRename, paths.renamedExe);
  var app = paths.renamedExe;

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
