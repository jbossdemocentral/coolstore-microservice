//  OpenShift sample Node application
var express = require('express'),
    http = require('http'),
    https = require('https'),
    fs      = require('fs'),
    app     = express(),
    path = require("path"),
    keycloakConfig = require('./app/keycloak.config.js'),
    coolstoreConfig = require('./app/coolstore.config.js');

var port = process.env.PORT || process.env.OPENSHIFT_NODEJS_PORT || 8080,
    ip   = process.env.IP   || process.env.OPENSHIFT_NODEJS_IP || '0.0.0.0',
    secport =  process.env.PORT || process.env.OPENSHIFT_NODEJS_PORT || 8443;

// error handling
app.use(function(err, req, res, next){
  console.error(err.stack);
  res.status(500).send('Something bad happened!');
});

// keycloak config server
app.get('/keycloak.json', function (req, res, next) {
  res.json(keycloakConfig);
});
// coolstore config server
app.get('/coolstore.json', function (req, res, next) {
  res.json(coolstoreConfig);
});

app.use(express.static(path.join(__dirname, '/views')));
app.use('/app', express.static(path.join(__dirname, '/app')));
app.use('/bower_components',  express.static(path.join(__dirname, '/bower_components')));


//app.listen(port, ip);
var keys = {
  key: fs.readFileSync('key.pem'),
  cert: fs.readFileSync('cert.pem')
};

http.createServer(app).listen(port);
https.createServer(keys, app).listen(secport);

console.log('HTTP Server running on http://%s:%s', ip, port);
console.log('HTTPS Server running on https://%s:%s', ip, secport);

module.exports = app ;