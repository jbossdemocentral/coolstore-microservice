'use strict';

angular.module("app")

.factory('cart', ['$http', '$q', 'COOLSTORE_CONFIG', '$location', function($http, $q, COOLSTORE_CONFIG, $location) {
	var factory = {}, cart;
			
	factory.checkout = function() {
		var deferred = $q.defer();
		$http({
			   method: 'POST',
			   url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_REST_ENDPOINT : COOLSTORE_CONFIG.REST_ENDPOINT) + '/products/checkout'
		   }).then(function(resp) {
			    cart = resp.data;
			   	deferred.resolve(resp.data);			   
		   }, function(err) {
			   	deferred.reject(err);
		   });
		return deferred.promise;
	}
	
	factory.reset = function() {
		cart = {};
		cart.shoppingCartItemList = [];
		$http({
			   method: 'GET',
			   url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_REST_ENDPOINT : COOLSTORE_CONFIG.REST_ENDPOINT) + '/products/cart',
		   }).then(function(resp) {
			    cart = resp.data;
		   }, function(err) {
		   });

	}
	
	factory.getCart = function() {
		return cart;
	}
		
	factory.removeFromCart = function(product, quantity) {
		var deferred = $q.defer();
		$http({
			   method: 'POST',
			   url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_REST_ENDPOINT : COOLSTORE_CONFIG.REST_ENDPOINT) + '/products/cart/delete',
			   data: [{
				   product: product,
				   quantity: quantity
			   }]
		   }).then(function(resp) {
			    cart = resp.data;
			   	deferred.resolve(resp.data);			   
		   }, function(err) {
			   	deferred.reject(err);
		   });
		return deferred.promise;

	}

	factory.addToCart = function(product, quantity) {
		var deferred = $q.defer();
		$http({
			   method: 'POST',
			   url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_REST_ENDPOINT : COOLSTORE_CONFIG.REST_ENDPOINT) + '/products/cart',
			   data: [{
				   product: product,
				   quantity: quantity
			   }]
		   }).then(function(resp) {
			    cart = resp.data;
			   	deferred.resolve(resp.data);			   
		   }, function(err) {
			   	deferred.reject(err);
		   });
		return deferred.promise;

	}
		
	factory.getProducts = function() {
		var deferred = $q.defer();
	   $http({
		   method: 'GET',
		   url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_REST_ENDPOINT : COOLSTORE_CONFIG.REST_ENDPOINT) + '/products/list'
	   }).then(function(resp) {
		  deferred.resolve(resp.data); 
	   }, function(err) {
		   deferred.reject(err);
	   });
	   return deferred.promise;
	}
	
	factory.reset();
	return factory;
}]);
