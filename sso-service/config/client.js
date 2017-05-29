
var request = require('request');
var sso_service_url=(process.env.SSO_SERVICE_URL || "http://sso:8080"),
  realm = (process.env.SSO_REALM || "coolstore"),
  sso_reg_user = (process.env.SSO_SERVICE_USER_NAME || "admin"),
  sso_reg_password = (process.env.SSO_SERVICE_USER_PASSWD || "coolstore"),
  coolstore_client_id = (process.env.COOLSTORE_CLIENT_ID || "web-ui"),
  coolstore_web_uri = (process.env.COOLSTORE_WEB_URI || "");



console.log("fetching access token");
request.post(
  {
    uri: sso_service_url + '/auth/realms/master/protocol/openid-connect/token',
    strictSSL: false,
    json: true,
    form: {
        username: sso_reg_user,
        password: sso_reg_password,
        grant_type: 'password',
        client_id: 'admin-cli'
    }
  },
  function (err, resp, body) {
    if (!err && resp.statusCode == 200) {
      var token = body.access_token;
      // console.log(token);
      request.post(
      {
          uri: sso_service_url  + '/auth/admin/realms/' + realm + '/clients',
          strictSSL: false,
          auth: {
              bearer: token
          },
          json: {
              clientId: coolstore_client_id,
              enabled: true,
              protocol: "openid-connect",
              redirectUris: [
                  'http://' + coolstore_web_uri + '/*',
                  'https://' + coolstore_web_uri + '/*'
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
      request.post(
        {
          uri: sso_service_url + '/auth/admin/realms/' + realm + '/roles',
          strictSSL: false,
          auth: {
              bearer: token
          },
          json: {
              name: 'user'
          }
      },
      function (err, resp, body) {
        if(!err && (resp.statusCode == 201 || resp.statusCode == 409) ) {

          //Successfull created role or role exists
          request.post(
          {
            uri: sso_service_url + '/auth/admin/realms/' + realm + '/users',
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
              requiredActions:[]
            }
          },
          function (err, resp, body) {
            if(!err && (resp.statusCode == 201 || resp.statusCode == 409) ) {
              request.get({
                uri: sso_service_url + '/auth/admin/realms/' + realm + '/users?username=appuser',
                strictSSL: false,
                auth: {
                  bearer: token
                },
                json: true
              },
              function (err, resp, body) {
                if(!err && resp.statusCode == 200 ) {
                  var userObj = body[0];
                  console.log("Id for user 'appdemo' is " + userObj.id);
                  // set temporary password
                  request.put({
                    uri: sso_service_url + '/auth/admin/realms/' + realm + '/users/' + userObj.id + '/reset-password' ,
                    strictSSL: false,
                    auth: {
                        bearer: token
                    },
                    json: {
                      type: 'password',
                      value: 'coolstore',
                      temporary: false
                    }
                  }, function(err, resp, body) {
                    if(!err && resp.statusCode == 204) {
                      console.log("Successfully reset password for user 'appdemo'");
                    }
                  });

                  console.log("fetching available roles");
                  request.get({
                    uri: sso_service_url + '/auth/admin/realms/' + realm + '/users/' + userObj.id + '/role-mappings/realm/available',
                    strictSSL: false,
                    auth: {
                        bearer: token
                    },
                    json: true
                  },
                  function (err, resp, body) {
                    if(!err && resp.statusCode == 200) {
                      var userRoleObj;
                      for(var i=0; i < body.length; i++) {
                        if(body[i].name == "user") {
                          userRoleObj = body[i];
                        }
                      }
                      if(userRoleObj != null) {
                        console.log("Id for role 'user' is " + userRoleObj.id);
                        request.post({
                          uri: sso_service_url + '/auth/admin/realms/' + realm + '/users/' + userObj.id + '/role-mappings/realm' ,
                          strictSSL: false,
                          auth: {
                              bearer: token
                          },
                          json: [
                            {
                              id: userRoleObj.id,
                              name: 'user',
                              scopeParamRequired: false,
                              composite: false
                            }
                          ]
                        },
                        function(err, resp, body) {
                          if(!err && resp.statusCode == 204) {
                            console.log("Successfully assigned role 'user' to user 'appdemo'");
                          }
                        });
                      } else {
                        console.log("Could not find available role 'user' for user 'appdemo'. Either the user already got the role assigned or the role does not exists.")
                      }
                    }
                    else {
                      console.error("Failed to get role-mappings with result " + ( err||resp.statusCode + " " + resp.statusMessage + " " + JSON.stringify(body) ));
                    }
                  });
                }
                else {
                  console.error("Failed to user 'appdemo' token with result " + ( err||resp.statusCode + " " + resp.statusMessage + " " + JSON.stringify(body) ));
                }
              });
            }
            else {
              console.error("Failed to create user 'appdemo' with result " + ( err||resp.statusCode + " " + resp.statusMessage + " " + JSON.stringify(body) ));
            }
          });
        }
        else {
          console.error("Failed to create role 'user' with result " + ( err||resp.statusCode + " " + resp.statusMessage + " " + JSON.stringify(body) ));
        }
      });
    }
    else {
      console.error("Failed to fetch token with result " + ( err||resp.statusCode + " " + resp.statusMessage + " " + JSON.stringify(body) ));
      throw new Error('Faled to connect to SSO service using URL' + sso_service_url + ', you might want to try again later.');
    }
  }
);
