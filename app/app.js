var app = require('app'),
  BrowserWindow = require('browser-window'),
  ipc = require('ipc'),
  path = require('path'),
  Menu = require('menu'),
  dialog = require('dialog'),
  config = {},
  fs = require('fs');
//
// report crashes to atom-shell
require('crash-reporter').start();

//--------------------------------------------------------------------------------
// Projects Config
//--------------------------------------------------------------------------------

var projectsFilename = path.join(app.getDataPath(), "projects.json");

function readProjectsConfig() {
  return JSON.parse(fs.readFileSync(projectsFilename, 'utf8'));
}

function writeProjectsConfig(projects) {
  fs.writeFileSync(projectsFilename, JSON.stringify(projects, null, 2), {encoding: "utf8"});
}

function addToProjectsConfig(filename) {
  var projects = readProjectsConfig();
  var i = projects.indexOf(filename);
  if (i === -1) {
    projects.push(filename);
    writeProjectsConfig(projects);
    mainWindow.webContents.send('add-existing-project', filename);
  }
}

function removeFromProjectsConfig(filename) {
  var projects = readProjectsConfig();
  var i = projects.indexOf(filename);
  if (i > -1) {
    projects.splice(i, 1);
    writeProjectsConfig(projects);
  }
  mainWindow.webContents.send('remove-project', filename);
}

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
      addToProjectsConfig(filename);
    }
  });
}

ipc.on("request-add-existing-project", function(event, arg) {
  console.log("request to add existing project received");
  showAddExistingProjectDialog();
});

ipc.on("request-remove-project", function(event, arg) {
  console.log("request to remove project received");
  removeFromProjectsConfig(arg);
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
