module.exports = function(grunt) {
'use strict';

require('shelljs/global');

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
  },

  appdmg: {
    options: {
      "title": "Cuttle",
      "background": "scripts/dmg/background.png",
      "icon-size": 80,
      "contents": [
        { "x": 448, "y": 344, "type": "link", "path": "/Applications" },
        { "x": 192, "y": 344, "type": "file", "path": "builds/latest-mac/Cuttle.app" }
      ]
    },
    target: {
      dest: "builds/latest-mac/Cuttle.dmg"
    }
  },

  winresourcer: {
    operation: "Update",
    exeFile: "builds/latest-windows/atom.exe", // TODO: make exe in static location
    resourceType: "Icongroup",
    resourceName: "1",
    resourceFile: "app/img/cuttle-logo.ico"
  },

});

//------------------------------------------------------------------------------
// Load Tasks
//------------------------------------------------------------------------------

// load tasks from npm
grunt.loadNpmTasks('grunt-contrib-less');
grunt.loadNpmTasks('grunt-contrib-watch');
grunt.loadNpmTasks('grunt-curl');
grunt.loadNpmTasks('grunt-download-atom-shell');
grunt.loadNpmTasks('grunt-appdmg');
grunt.loadNpmTasks('grunt-winresourcer');

grunt.registerTask('default', ['watch']);

//------------------------------------------------------------------------------
// Custom Tasks
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

grunt.registerTask('fresh-build', function() {
  exec("lein cljsbuild clean");
  exec("lein cljsbuild once");
  grunt.task.run("less");
});

// end module.exports
};
