module.exports = function(grunt) {
'use strict';

//------------------------------------------------------------------------------
// Grunt Config
//------------------------------------------------------------------------------

grunt.initConfig({

  // LESS conversion
  less: {
    options: {
      compress: true
    },

    default: {
      files: {
        'public/css/main.min.css': 'less/main.less',
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
  }

});

//------------------------------------------------------------------------------
// Load Tasks
//------------------------------------------------------------------------------

// load tasks from npm
grunt.loadNpmTasks('grunt-contrib-less');
grunt.loadNpmTasks('grunt-contrib-watch');

grunt.registerTask('default', ['watch']);

// end module.exports
};