'use strict';

angular.module("app")

.factory('cart', ['$http', '$q', function($http, $q) {
	var factory = {}, cart;
			
	factory.checkout = function() {
		var deferred = $q.defer();
		$http({
			   method: 'POST',
			   url: 'rest/products/checkout'
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
			   url: 'rest/products/cart',
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
			   url: 'rest/products/cart/delete',
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
			   url: 'rest/products/cart',
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
		   url: 'rest/products/list'
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
