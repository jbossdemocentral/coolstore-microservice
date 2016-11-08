'use strict';

var module = angular.module('app', ['ngRoute', 'patternfly']), auth = {
    loggedIn: false,
    ssoEnabled: false,
    logout: function () {
    }
};

module.factory('Auth', function () {
    return auth;
});

angular.element(document).ready(function () {

    // get config
    var initInjector = angular.injector(["ng"]);
    var $http = initInjector.get("$http");

    $http.get("coolstore.json").then(function (response) {
        module.constant("COOLSTORE_CONFIG", response.data);
        
        if (!response.data.SSO_ENABLED) {
            angular.bootstrap(document, ["app"], {
                strictDi: true
            });
        } else {
            auth.ssoEnabled = true;
            var keycloakAuth = new Keycloak('keycloak.json');
            auth.loggedIn = false;

            auth.login = function () {
                keycloakAuth.login({
                    loginHint: 'appuser'
                });
            };

            keycloakAuth.init({
                onLoad: 'check-sso'
            }).success(function () {
                if (keycloakAuth.authenticated) {
                    keycloakAuth.loadUserInfo().success(function (userInfo) {
                        auth.userInfo = userInfo;
                        angular.bootstrap(document, ["app"], {
                            strictDi: true
                        });
                        auth.loggedIn = true;
                        auth.authz = keycloakAuth;
                        auth.logout = function () {
                            auth.loggedIn = false;
                            auth.authz = null;
                            auth.userInfo = {};
                            keycloakAuth.logout();
                        };
                    }).error(function () {
                        angular.bootstrap(document, ["app"], {
                            strictDi: true
                        });

                    });
                } else {
                    angular.bootstrap(document, ["app"], {
                        strictDi: true
                    });
                }
            }).error(function (msg) {
                angular.bootstrap(document, ["app"], {
                    strictDi: true
                });
            });
        }
    });
});


// setup interceptors
module.config(['$httpProvider', function ($httpProvider) {

    $httpProvider.interceptors.push(['$q', 'Auth', function ($q, Auth) {
        return {
            'request': function (config) {
                var deferred = $q.defer();
                if (Auth.authz && Auth.authz.token) {
                    Auth.authz.updateToken(5).success(function () {
                        config.headers = config.headers || {};
                        config.headers.Authorization = 'Bearer ' + Auth.authz.token;
                        config.withCredentials = true;
                        deferred.resolve(config);
                    }).error(function () {
                        deferred.reject('Failed to refresh token');
                    });
                } else {
                    config.withCredentials = false;
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

