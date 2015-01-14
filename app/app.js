var app = require('app'),
  BrowserWindow = require('browser-window'),
  dialog = require('dialog'),
  fs = require('fs')
  ipc = require('ipc'),
  Menu = require('menu'),
  path = require('path');

// report crashes to atom-shell
require('crash-reporter').start();

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
    mainWindow.webContents.send('new-project-folder', folders[0]);
  }
}

function showNewProjectDialog() {
  dialog.showOpenDialog(newProjectDialogOptions, newProjectDialogSuccess);
}

ipc.on('request-add-existing-project-dialog', showAddExistingProjectDialog);
ipc.on('request-new-project-folder-dialog', showNewProjectDialog);

//------------------------------------------------------------------------------
// Menu Builder
//------------------------------------------------------------------------------

const menuTemplate = [
  {
    label: 'File', // NOTE: On Mac, the first menu item is always the name of the Application
                   //       (uses CFBundleName in Info.plist, set by "release.sh")
    submenu: [
      {
        label: 'Add Existing Project',
        click: showAddExistingProjectDialog
      }
    ]
  }
];

var menu = Menu.buildFromTemplate(menuTemplate);

// FIXME: custom menu disabled, since CMD+Q doesn't work without the menu item for it
// Menu.setApplicationMenu(menu);

//------------------------------------------------------------------------------
// Main
//------------------------------------------------------------------------------

// load development config (optional)
var devConfig = {};
if (fs.existsSync(__dirname + '/config.json')) {
  devConfig = require(__dirname + '/config.json');
}

// load window information
const windowInformationFile = app.getDataPath() + path.sep + 'window.json';
var windowInformation = {};
if (fs.existsSync(windowInformationFile)) {
  windowInformation = require(windowInformationFile);
}

// Keep a global reference of the window object, if you don't, the window will
// be closed automatically when the javascript object is GCed.
var mainWindow = null;

/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// dock bounce on Mac (disabled for now)

var bounceID;

function onStartBounce() {
  // run bounce and set to bounceID
  bounceID = app.dock.bounce("critical");
  console.log(bounceID);

  // test code to see how setBadge works
  app.dock.setBadge("E");
}

ipc.on('start-bounce', onStartBounce);

function onStopBounce() {
  // need to get bounceID as returned from app.dock.bounce() and pass to
  // app.dock.cancelBounce(id)
  app.dock.cancelBounce(0);
}

ipc.on('stop-bounce', onStopBounce);
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

function shutdownForReal() {
  // save current window information
  windowInformation.maximized = mainWindow.isMaximized();
  windowInformation.position = mainWindow.getPosition();
  windowInformation.size = mainWindow.getSize();
  fs.writeFileSync(windowInformationFile, JSON.stringify(windowInformation));

  // NOTE: so the docs say to only use this when the page has crashed, but I think
  // it's ok in this case because of the way we're "trapping" the regular close event
  // https://github.com/atom/atom-shell/blob/master/docs/api/browser-window.md#browserwindowdestroy
  // TODO: look into using app.quit() here instead
  mainWindow.destroy();
}

ipc.on('shutdown-for-real', shutdownForReal);

// NOTE: copied this directly from the atom-shell docs
// is this really necessary?
function onWindowClosed() {
  // dereference the window object
  mainWindow = null;
}

function onWindowClose(evt) {
  // prevent window close
  evt.preventDefault();

  // send shutdown signal to the window
  mainWindow.webContents.send('shutdown');
}

// send the OS-normalized app data path to the webpage
// NOTE: this event triggers the "global app init" on the webpage side
function onFinishLoad() {
  mainWindow.webContents.send('config-file-location', app.getDataPath());
}

// NOTE: not all of the browserWindow options listed on the docs page work
// on all operating systems
const browserWindowOptions = {
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
    mainWindow.maximize();
  }
  else if (windowInformation.hasOwnProperty('size') &&
           windowInformation.hasOwnProperty('position')) {
    mainWindow.setPosition(windowInformation.position[0], windowInformation.position[1]);
    mainWindow.setSize(windowInformation.size[0], windowInformation.size[1]);
  }
}

// This method will be called when atom-shell has done everything
// initialization and ready for creating browser windows.
app.on('ready', startApp);
