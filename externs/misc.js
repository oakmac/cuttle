var fs = {};
fs.existsSync = function() {};
fs.mkdirSync = function() {};
fs.readFileSync = function() {};
fs.rmrfSync = function() {};
fs.writeFileSync = function() {};
fs.unlinkSync = function() {};

var ipc = {};
ipc.on = function() {};

var moment = {};
moment.format = function() {};
moment.unix = function() {};

var path = {};
path.dirname = function() {};

// NOTE: this is NOT the correct way to do externs...
Object.prototype.setEncoding = function() {};
Object.prototype.stderr = {};
Object.prototype.stdout = {};
