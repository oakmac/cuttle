var app = require('app'),
  BrowserWindow = require('browser-window'),
  dialog = require('dialog'),
  fs = require('fs')
  ipc = require('ipc'),
  Menu = require('menu'),
  path = require('path'),
  winston = require('winston'),
  packageJson = require(__dirname + '/package.json');

// report crashes to atom-shell
require('crash-reporter').start();

// Keep a global reference of the window object, if you don't, the window will
// be closed automatically when the javascript object is GCed.
var mainWindow = null;

//------------------------------------------------------------------------------
// Logging
//------------------------------------------------------------------------------

const isDev = (packageJson.version.indexOf("DEV") !== -1);
const onMac = (process.platform === 'darwin');

function getLogPath() {
  // put cuttle.log in /app in the project folder if we're in dev mode
  if (isDev) {
    return __dirname;
  }

  // cuttle.log goes here on a mac so it can be used by Console app
  if (onMac) {
    return path.join(process.env['HOME'], "Library", "Logs");
  }

  // else use the app path folder
  return app.getDataPath();
}

const winstonFileOptions = {
  filename: getLogPath() + path.sep + "cuttle.log",
  json: false,
  maxFiles: 10,
  maxsize: 10000000, // 10MB
  prettyPrint: true,
  tailable: true,
  timestamp: true
};

// add logger
winston.add(winston.transports.File, winstonFileOptions);

ipc.on('log-info',  function(e, msg) { winston.info("client:", msg); });
ipc.on('log-warn',  function(e, msg) { winston.warn("client:", msg); });
ipc.on('log-error', function(e, msg) { winston.error("client:", msg); });

winston.info("-------------------------------------------");
winston.info("Cuttle started");

winston.info("build version:", packageJson["version"]);
winston.info("build date:   ", packageJson["build-date"]);
winston.info("build commit: ", packageJson["build-commit"]);
winston.info("-------------------------------------------");

//------------------------------------------------------------------------------
// Dialogs
// (These have to be created on the "browser" side, i.e. here, not on the "page")
//------------------------------------------------------------------------------

const existingProjectDialogOptions = {
  title: 'Please select an existing project.clj file',
  properties: ['openFile'],
  filters: [
    {
      name: 'Leiningen project.clj',
      extensions: ['clj']
    }
  ]
};

function showAddExistingProjectDialog() {
  winston.info("showing existing-project dialog");
  dialog.showOpenDialog(existingProjectDialogOptions, function(filenames) {
    if (filenames) {
      var filename = filenames[0];
      mainWindow.webContents.send('add-existing-project-dialog-success', filename);
    }
  });
}

const newProjectDialogOptions = {
  title: 'Select a folder for your new project',
  properties: ['openDirectory']
};

function newProjectDialogSuccess(folders) {
  if (folders) {
    winston.info("new-project dialog selected folders", folders);
    mainWindow.webContents.send('new-project-folder', folders[0]);
  }
  else {
    winston.info("new-project dialog cancelled");
  }
}

function showNewProjectDialog() {
  winston.info("showing new project dialog");
  dialog.showOpenDialog(newProjectDialogOptions, newProjectDialogSuccess);
}

ipc.on('request-add-existing-project-dialog', showAddExistingProjectDialog);
ipc.on('request-new-project-folder-dialog', showNewProjectDialog);

//------------------------------------------------------------------------------
// Main
//------------------------------------------------------------------------------

// load development config (optional)
const devConfigFile = __dirname + '/config.json';
var devConfig = {};
if (fs.existsSync(devConfigFile)) {
  winston.info("loading optional dev config", devConfigFile);
  devConfig = require(devConfigFile);
}

// load window information
const windowInformationFile = app.getDataPath() + path.sep + 'window.json';
var windowInformation = {};
if (fs.existsSync(windowInformationFile)) {
  winston.info("loading window information", windowInformationFile);
  windowInformation = require(windowInformationFile);
}

function onBounceDock() {
  winston.info("trying to make dock bounce");
  if (app &&
      app.hasOwnProperty('dock') &&
      app.dock.hasOwnProperty('bounce')) {
    winston.info("making dock bounce");
    app.dock.bounce();
  }
}

ipc.on('bounce-dock', onBounceDock);

var shutdownForRealHasHappened = false;

function shutdownForReal() {
  winston.info("starting to shut down for real");

  // save current window information
  windowInformation.maximized = mainWindow.isMaximized();
  windowInformation.position = mainWindow.getPosition();
  windowInformation.size = mainWindow.getSize();

  // it's not super-important that this succeeds
  // https://github.com/oakmac/cuttle/issues/74
  try {
    winston.info("saving window information", windowInformationFile);
    fs.writeFileSync(windowInformationFile, JSON.stringify(windowInformation));
  }
  catch (e) {
    // do nothing
    winston.warn("couldn't save window information");
  }

  // toggle the shutdown for real flag and close the window
  shutdownForRealHasHappened = true;
  mainWindow.close();
}

ipc.on('shutdown-for-real', shutdownForReal);

// NOTE: copied this directly from the atom-shell docs
// is this really necessary?
function onWindowClosed() {
  winston.info("window closed");

  // dereference the window object
  mainWindow = null;
}

function onWindowClose(evt) {
  winston.info("window trying to close");

  // prevent window close if we have not shut down for real
  if (shutdownForRealHasHappened !== true) {
    evt.preventDefault();

    // send shutdown signal to the window
    winston.info("sending client shutdown attempt");
    mainWindow.webContents.send('shutdown-attempt');
  }
}

// send the OS-normalized app data path to the webpage
// NOTE: this event triggers the "global app init" on the webpage side
function onFinishLoad() {
  var path = app.getDataPath();
  winston.info("sending path to client", path);
  mainWindow.webContents.send('config-file-location', path);
}

// NOTE: not all of the browserWindow options listed on the docs page work
// on all operating systems
const browserWindowOptions = {
  height: 850,
  icon: __dirname + '/img/cuttle-logo.png',
  title: 'Cuttle',
  width: 1000
};

function startApp() {
  winston.info("atom-shell ready event triggered, starting app");

  // create the browser window
  winston.info("creating browser window");
  mainWindow = new BrowserWindow(browserWindowOptions);

  // load index.html
  mainWindow.loadUrl('file://' + __dirname + '/index.html');

  // send info to the webpage
  mainWindow.webContents.on('did-finish-load', onFinishLoad);

  // Emitted when the user tries to close the window
  mainWindow.on('close', onWindowClose);

  // Emitted when the window is closed.
  mainWindow.on('closed', onWindowClosed);

  // optionally launch dev tools
  if (devConfig.hasOwnProperty('dev-tools') &&
      devConfig['dev-tools'] === true) {
    mainWindow.openDevTools();
  }

  // position window initially
  if (windowInformation.hasOwnProperty('maximized') &&
      windowInformation.maximized === true) {
    winston.info("maximizing window");
    mainWindow.maximize();
  }
  else if (windowInformation.hasOwnProperty('size') &&
           windowInformation.hasOwnProperty('position')) {
    winston.info("setting window size and x,y position");
    mainWindow.setPosition(windowInformation.position[0], windowInformation.position[1]);
    mainWindow.setSize(windowInformation.size[0], windowInformation.size[1]);
  }
}

// This method will be called when atom-shell has done everything
// initialization and ready for creating browser windows.
app.on('ready', startApp);

// Quit the application when all windows are closed.
app.on('window-all-closed', function() {
  winston.info("all windows closed; exiting app");
  app.quit();
});
