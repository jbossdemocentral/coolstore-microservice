'use strict';

angular.module('app').config([ '$routeProvider', function($routeProvider) {
  $routeProvider.when('/', {
    templateUrl : 'partials/home.html',
    controller : 'HomeController'
  }).when('/cart', {
    templateUrl : 'partials/cart.html',
    controller : 'CartController'
  }).otherwise({
    redirectTo : '/'
  });
} ]);
