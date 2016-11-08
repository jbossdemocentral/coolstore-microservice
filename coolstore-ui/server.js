//  OpenShift sample Node application
var express = require('express'),
    http = require('http'),
    request = require('request'),
    fs = require('fs'),
    app = express(),
    path = require("path"),
    keycloakConfig = require('./app/keycloak.config.js'),
    coolstoreConfig = require('./app/coolstore.config.js');

var port = process.env.PORT || process.env.OPENSHIFT_NODEJS_PORT || 8080,
    ip = process.env.IP || process.env.OPENSHIFT_NODEJS_IP || '0.0.0.0',
    secport = process.env.PORT || process.env.OPENSHIFT_NODEJS_PORT || 8443;

// error handling
app.use(function (err, req, res, next) {
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
app.use('/bower_components', express.static(path.join(__dirname, '/bower_components')));

console.log("coolstore config: " + JSON.stringify(coolstoreConfig));
console.log("keycloak config: " + JSON.stringify(keycloakConfig));

if (process.env.SSO_URL) {
// register client
    console.log("fetching access token");
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

    }, function (err, resp, body) {
        if (!err && resp.statusCode == 200) {
            console.log("Access token result: " + resp.statusCode + " " + resp.statusMessage + " " + JSON.stringify(body));
            var token = body.access_token;


            // register client
            console.log("registering client '" + process.env.SSO_CLIENT_ID + "' in realm '" + process.env.SSO_REALM + "' at " + process.env.SSO_URL || process.env.SSO_SERVICE_URL);
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
            }, function (err, resp, body) {
                console.log("register client result: " + resp.statusCode + " " + resp.statusMessage + " " + JSON.stringify(body));
            });

            // create role
            console.log("creating role 'user'");
            request.post({
                uri: (process.env.SSO_URL || process.env.SSO_SERVICE_URL) + '/admin/realms/' + process.env.SSO_REALM + '/roles',
                strictSSL: false,
                auth: {
                    bearer: token
                },
                json: {
                    name: 'user'
                }
            }, function (err, resp, body) {
                console.log("Role 'user' creation result: " + resp.statusCode + " " + resp.statusMessage + " " + JSON.stringify(body));

                // create user
                console.log("creating user 'appuser'");
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
                }, function (err, resp, body) {

                    console.log("User 'appuser' creation result: " + resp.statusCode + " " + resp.statusMessage + " " + JSON.stringify(body));

                    // assign appuser to the 'user' role
                    console.log("fetching user 'appuser'");
                    request.get({
                        uri: (process.env.SSO_URL || process.env.SSO_SERVICE_URL) + '/admin/realms/' + process.env.SSO_REALM + '/users?username=appuser',
                        strictSSL: false,
                        auth: {
                            bearer: token
                        },
                        json: true
                    }, function (err, resp, body) {
                        console.log("Fetch user 'appuser' result: " + resp.statusCode + " " + resp.statusMessage + " " + JSON.stringify(body));

                        var userObj = body[0];

                        console.log("fetching available roles");
                        request.get({
                            uri: (process.env.SSO_URL || process.env.SSO_SERVICE_URL) + '/admin/realms/' + process.env.SSO_REALM + '/users/' + userObj.id + '/role-mappings/realm/available',
                            strictSSL: false,
                            auth: {
                                bearer: token
                            },
                            json: true
                        }, function (err, resp, body) {
                            console.log("Fetch available roles result: " + resp.statusCode + " " + resp.statusMessage + " " + JSON.stringify(body));

                            var allRoles = body;

                            console.log("assigning all available roles to user");
                            request.post({
                                uri: (process.env.SSO_URL || process.env.SSO_SERVICE_URL) + '/admin/realms/' + process.env.SSO_REALM + '/users/' + userObj.id + '/role-mappings/realm',
                                strictSSL: false,
                                auth: {
                                    bearer: token
                                },
                                json: allRoles

                            }, function (err, resp, body) {
                                console.log("Role assignment result: " + resp.statusCode + " " + resp.statusMessage + " " + JSON.stringify(body));
                            });
                        });

                        // set temporary password
                        request.put({
                            uri: (process.env.SSO_URL || process.env.SSO_SERVICE_URL) + '/admin/realms/' + process.env.SSO_REALM + '/users/' + userObj.id + '/reset-password' ,
                            strictSSL: false,
                            auth: {
                                bearer: token
                            },
                            json: {
                                type: 'password',
                                value: 'password',
                                temporary: true
                            }
                        }, function(err, resp, body) {
                            console.log("Reset password result: " + resp.statusCode + " " + resp.statusMessage + " " + JSON.stringify(body));
                        });
                    });
                });
            });
        }
    });
} else {
    console.log("Skipping SSO configuration: missing SSO_URL");
}

http.createServer(app).listen(port);

console.log('HTTP Server running on http://%s:%s', ip, port);

module.exports = app;