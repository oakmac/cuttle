module.exports = function(grunt) {
'use strict';

//------------------------------------------------------------------------------
// Grunt Config
//------------------------------------------------------------------------------

const leinJarUrl = 'https://github.com/technomancy/leiningen/releases/download/2.5.0/leiningen-2.5.0-standalone.jar';

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

// end module.exports
};
