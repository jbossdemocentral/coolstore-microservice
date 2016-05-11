'use strict';

var module = angular.module('app', [ 'ngRoute', 'patternfly' ]), auth = {
  logout : function() {
  }
};

angular.element(document).ready(function($http) {
  var keycloakAuth = new Keycloak('keycloak.json');
  auth.loggedIn = false;

  keycloakAuth.init({
    onLoad : 'login-required'
  }).success(function() {
    auth.loggedIn = true;
    auth.authz = keycloakAuth;
    auth.logout = function() {
      console.log('*** LOGOUT');
      auth.loggedIn = false;
      auth.authz = null;
      auth.userInfo = {};
      keycloakAuth.logout();
    }
    module.factory('Auth', function() {
      return auth;
    });
    keycloakAuth.loadUserInfo().success(function(userInfo) {
      auth.userInfo = userInfo;
    });

    angular.bootstrap(document, [ "app" ], {
      strictDi : true
    });
  }).error(function() {
    window.location.reload();
  });

});

module.config([ '$httpProvider', function($httpProvider) {
  $httpProvider.interceptors.push([ '$q', 'Auth', function($q, Auth) {
    return {
      'request' : function(config) {
        var deferred = $q.defer();
        if (Auth.authz && Auth.authz.token) {
          Auth.authz.updateToken(5).success(function() {
            config.headers = config.headers || {};
            config.headers.Authorization = 'Bearer ' + Auth.authz.token;

            deferred.resolve(config);
          }).error(function() {
            deferred.reject('Failed to refresh token');
          });
        }
        return deferred.promise;

      },
      'responseError' : function(response) {
        if (response.status == 401) {
          console.log('session timeout?');
          auth.logout();
        }
        return $q.reject(response);

      }
    }
  } ]);
} ]);

