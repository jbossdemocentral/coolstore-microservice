'use strict';

var module = angular.module('app', ['ngRoute', 'patternfly']), auth = {
    logout: function () {
    }
};

angular.element(document).ready(function () {

    // get config
    var initInjector = angular.injector(["ng"]);
    var $http = initInjector.get("$http");
    $http.get("coolstore.json").then(function (response) {
        module.constant("COOLSTORE_CONFIG", response.data);
        var keycloakAuth = new Keycloak('keycloak.json');
        keycloakAuth.responseMode = 'query';
        auth.loggedIn = false;

        auth.login = function () {
            keycloakAuth.login({
                loginHint: 'appuser'
            });
        };
        module.factory('Auth', function () {
            return auth;
        });

        var tokens = {
            token: localStorage.getItem('token'),
            refreshToken: localStorage.getItem('refreshToken'),
            idToken: localStorage.getItem('idToken')
        };

        keycloakAuth.init(tokens).success(function () {
            if (keycloakAuth.authenticated) {
                localStorage.setItem("token", keycloakAuth.token);
                localStorage.setItem("idToken", keycloakAuth.token);
                localStorage.setItem("refreshToken", keycloakAuth.token);
                keycloakAuth.loadUserInfo().success(function (userInfo) {
                    auth.userInfo = userInfo;
                    angular.bootstrap(document, ["app"], {
                        strictDi: true
                    });
                });

                auth.loggedIn = true;
                auth.authz = keycloakAuth;
                auth.logout = function () {
                    localStorage.removeItem('token');
                    localStorage.removeItem('idToken');
                    localStorage.removeItem('refreshToken');
                    auth.loggedIn = false;
                    auth.authz = null;
                    auth.userInfo = {};
                    keycloakAuth.logout();
                };

            } else {
                angular.bootstrap(document, ["app"], {
                    strictDi: true
                });
            }
        }).error(function(msg) {
            angular.bootstrap(document, ["app"], {
                strictDi: true
            });


        });

    });

});



// setup interceptors
module.config(['$httpProvider', function ($httpProvider) {

    $httpProvider.defaults.withCredentials = true;

    $httpProvider.interceptors.push(['$q', 'Auth', function ($q, Auth) {
        return {
            'request': function (config) {
                var deferred = $q.defer();
                if (Auth.authz && Auth.authz.token) {
                    Auth.authz.updateToken(5).success(function () {
                        config.headers = config.headers || {};
                        config.headers.Authorization = 'Bearer ' + Auth.authz.token;

                        deferred.resolve(config);
                    }).error(function () {
                        deferred.reject('Failed to refresh token');
                    });
                } else {
                    deferred.resolve(config);
                }
                return deferred.promise;

            },
            'responseError': function (response) {
                if (response.status == 401) {
                    auth.logout();
                }
                return $q.reject(response);

            }
        }
    }]);
}]);

