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
  }

});

//------------------------------------------------------------------------------
// Load Tasks
//------------------------------------------------------------------------------

// load tasks from npm
grunt.loadNpmTasks('grunt-contrib-less');
grunt.loadNpmTasks('grunt-contrib-watch');
grunt.loadNpmTasks('grunt-curl');
grunt.loadNpmTasks('grunt-download-atom-shell');

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

// end module.exports
};
