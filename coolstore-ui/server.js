//  OpenShift sample Node application
var express = require('express'),
    http = require('http'),
    https = require('https'),
    request = require('request'),
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

// register client
console.log("registering client '" + process.env.SSO_CLIENT_ID + "' in realm '" + process.env.SSO_REALM + "' at " + process.env.SSO_URL ||  process.env.SSO_SERVICE_URL);

// request.debug = true;
request.post({
  uri: (process.env.SSO_URL || process.env.SSO_SERVICE_URL) + '/realms/' + process.env.SSO_REALM + '/protocol/openid-connect/token',
  strictSSL: false,
  json: true,
  form: {
    username: process.env.SSO_USERNAME,
    password: process.env.SSO_PASSWORD,
    grant_type: 'password',
    client_id: 'admin-cli'
  }

}, function(err, resp, body) {
  if (!err && resp.statusCode == 200) {
    var token = body.access_token;

    // register client
    request.post({
      uri: (process.env.SSO_URL || process.env.SSO_SERVICE_URL) + '/admin/realms/' + process.env.SSO_REALM + '/clients',
      strictSSL: false,
      auth: {
        bearer: token
      },
      json: {
        clientId: process.env.SSO_CLIENT_ID || 'aclient',
        enabled: true,
        protocol: "openid-connect",
        redirectUris: [
          'http://' + process.env.HOSTNAME_HTTP + '/*',
          'https://' + process.env.HOSTNAME_HTTPS + '/*'
        ],
        webOrigins: [
          "*"
        ],
        "bearerOnly": false,
        "publicClient": true
      }
    }, function(err, resp, body) {
      if (!err && resp.statusCode == 201) {
        console.log("Client registered");
      } else {
        console.log("error registering client: " + JSON.stringify(body));
      }
    });

    // create role
    request.post({
      uri: (process.env.SSO_URL || process.env.SSO_SERVICE_URL) + '/admin/realms/' + process.env.SSO_REALM + '/roles',
      strictSSL: false,
      auth: {
        bearer: token
      },
      json: {
        name: 'user'
      }
    }, function(err, resp, body) {
      if (!err && resp.statusCode == 201) {
        console.log("Role 'user' created");

        // create user and assign to role
        request.post({
          uri: (process.env.SSO_URL || process.env.SSO_SERVICE_URL) + '/admin/realms/' + process.env.SSO_REALM + '/users',
          strictSSL: false,
          auth: {
            bearer: token
          },
          json: {
            username: 'appuser',
            enabled: true,
            emailVerified: true,
            firstName: 'Joe',
            lastName: 'User',
            email: 'joeuser@nowhere.com',
            realmRoles: ['user'],
            credentials: [{
              type: 'password',
              value: 'password',
              temporary: false
            }]
          }
        }, function(err, resp, body) {
          if (!err && resp.statusCode == 201) {
            console.log("User 'appuser' created");
          } else {
            console.log("error creating user 'appuser': " + JSON.stringify(body));
          }
        });
      } else {
        console.log("error creating role 'user': " + JSON.stringify(body));
      }
    });


  } else {
    console.log("error fetching admin token: " + JSON.stringify(body));
  }
});

console.log("coolstore config: " + JSON.stringify(coolstoreConfig));
console.log("keycloak config: " + JSON.stringify(keycloakConfig));

var keys = {
  key: fs.readFileSync('key.pem'),
  cert: fs.readFileSync('cert.pem')
};

http.createServer(app).listen(port);
https.createServer(keys, app).listen(secport);

console.log('HTTP Server running on http://%s:%s', ip, port);
console.log('HTTPS Server running on https://%s:%s', ip, secport);

module.exports = app ;