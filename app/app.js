var app = require('app'),
  BrowserWindow = require('browser-window'),
  ipc = require('ipc'),
  Menu = require('menu'),
  dialog = require('dialog'),
  config = {},
  fs = require('fs');
//
// report crashes to atom-shell
require('crash-reporter').start();

//--------------------------------------------------------------------------------
// Dialogs
// (These have to be created on the "browser" side, i.e. here, not on the "page")
//--------------------------------------------------------------------------------

function showAddExistingProjectDialog() {
  var options = {
    title: "Select an existing project (project.clj)",
    properties: ["openFile"],
    filters: [
      {
        name: "Leiningen project config",
        extensions: ["clj"]
      }
    ]
  };

  dialog.showOpenDialog(options, function(filenames) {
    if (filenames) {
      var filename = filenames[0];
      mainWindow.webContents.send("add-existing-project-dialog-success", filename);
    }
  });
}

ipc.on("request-add-existing-project-dialog", function(event, arg) {
  showAddExistingProjectDialog();
});

//--------------------------------------------------------------------------------
// Menu Builder
//--------------------------------------------------------------------------------

var menuTemplate = [
  {
    label: "File", // NOTE: On Mac, the first menu item is always the name of the Application
                   //       (uses CFBundleName in Info.plist, set by "release.sh")
    submenu: [
      {
        label: "Add Existing Project",
        click: showAddExistingProjectDialog
      }
    ]
  }
];

var menu = Menu.buildFromTemplate(menuTemplate);

// FIXME: custom menu disabled, since CMD+Q doesn't work without the menu item for it
// Menu.setApplicationMenu(menu);

//--------------------------------------------------------------------------------
// Main
//--------------------------------------------------------------------------------

// load config
// NOTE: this is mostly for development purposes
if (fs.existsSync(__dirname + '/config.json')) {
  config = require(__dirname + '/config.json');
}

// Keep a global reference of the window object, if you don't, the window will
// be closed automatically when the javascript object is GCed.
var mainWindow = null;

function onWindowClose() {
  // dereference the window object
  mainWindow = null;
}

// send the OS-normalized app data path to the webpage
// NOTE: this event triggers the "global app init" on the webpage side
function onFinishLoad() {
  mainWindow.webContents.send('config-file-location', app.getDataPath());
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

  // send info to the webpage
  mainWindow.webContents.on('did-finish-load', onFinishLoad);

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
