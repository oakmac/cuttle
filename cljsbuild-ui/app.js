var app = require('app'),
  BrowserWindow = require('browser-window'),
  config = {},
  fs = require('fs');

// report crashes to atom-shell
require('crash-reporter').start();

// load config
if (fs.existsSync(__dirname + '/config.json')) {
  config = require(__dirname + '/config.json');
}

// Keep a global reference of the window object, if you don't, the window will
// be closed automatically when the javascript object is GCed.
var mainWindow = null;

// Quit when all windows are closed.
app.on('window-all-closed', function() {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

function onWindowClose() {
  // dereference the window object
  mainWindow = null;
}

// NOTE: a lot of the browserWindow options listed on the docs page aren't
// working - need to investigate
var browserWindowOptions = {
  height: 850,
  icon: __dirname + '/img/clojure-logo.png',
  title: 'ClojureScript Compiler',
  width: 1000
};

function startApp() {
  // create the browser window
  mainWindow = new BrowserWindow(browserWindowOptions);

  // load index.html
  mainWindow.loadUrl('file://' + __dirname + '/index.html');

  // Emitted when the window is closed.
  mainWindow.on('closed', onWindowClose);

  // optionally launch dev tools
  if (config.hasOwnProperty("dev-tools") && config["dev-tools"] === true) {
    mainWindow.openDevTools();
  }
}

// This method will be called when atom-shell has done everything
// initialization and ready for creating browser windows.
app.on('ready', startApp);