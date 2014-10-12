var app = require('app'),
  BrowserWindow = require('browser-window');

// report crashes to atom-shell
require('crash-reporter').start();

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
  title: "ClojureScript Compiler",
  width: 1000
};

function startApp() {
  // create the browser window
  mainWindow = new BrowserWindow(browserWindowOptions);

  // load index.html
  mainWindow.loadUrl('file://' + __dirname + '/index.html');

  // Emitted when the window is closed.
  mainWindow.on('closed', onWindowClose);

  // TODO: make this operate with a config
  mainWindow.openDevTools();
}

// This method will be called when atom-shell has done everything
// initialization and ready for creating browser windows.
app.on('ready', startApp);