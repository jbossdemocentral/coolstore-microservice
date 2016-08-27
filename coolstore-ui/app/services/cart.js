'use strict';

angular.module("app")

.factory('cart', ['$http', '$q', 'COOLSTORE_CONFIG', '$location', function($http, $q, COOLSTORE_CONFIG, $location) {
	var factory = {}, cart, products, cartId;

	factory.checkout = function() {
		var deferred = $q.defer();
		$http({
			   method: 'POST',
			   url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_API_ENDPOINT : COOLSTORE_CONFIG.API_ENDPOINT) + '/products/cart/checkout/' + cartId
		   }).then(function(resp) {
			    cart = resp.data;
			   	deferred.resolve(resp.data);
		   }, function(err) {
			   	deferred.reject(err);
		   });
		return deferred.promise;
	};

	factory.reset = function() {
		cart = {};
		cartId = auth.userInfo.sub;
		cart.shoppingCartItemList = [];
		$http({
			   method: 'GET',
			   url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_API_ENDPOINT : COOLSTORE_CONFIG.API_ENDPOINT) + '/products/cart/' + cartId
		   }).then(function(resp) {
			    cart = resp.data;
		   }, function(err) {
		   });

	};

	factory.getCart = function() {
		return cart;
	};

	factory.removeFromCart = function(product, quantity) {
		var deferred = $q.defer();
		$http({
			   method: 'DELETE',
			   url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_API_ENDPOINT : COOLSTORE_CONFIG.API_ENDPOINT) + '/products/cart/' + cartId + '/' + product.itemId + '/' + quantity
		   }).then(function(resp) {
			console.log("delete: got response: " + JSON.stringify(resp));
			    cart = resp.data;
			   	deferred.resolve(resp.data);
		   }, function(err) {
			   	deferred.reject(err);
		   });
		return deferred.promise;

	};

	factory.addToCart = function(product, quantity) {
		var deferred = $q.defer();
		$http({
			   method: 'POST',
			   url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_API_ENDPOINT : COOLSTORE_CONFIG.API_ENDPOINT) + '/products/cart/' + cartId + '/' + product.itemId + '/' + quantity
		   }).then(function(resp) {
			console.log("add: got response: " + JSON.stringify(resp));
			    cart = resp.data;
			   	deferred.resolve(resp.data);
		   }, function(err) {
			   	deferred.reject(err);
		   });
		return deferred.promise;

	};

	factory.getProducts = function() {
		var deferred = $q.defer();
        if (products) {
            deferred.resolve(products);
        } else {
            $http({
                method: 'GET',
                url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_API_ENDPOINT : COOLSTORE_CONFIG.API_ENDPOINT) + '/products'
            }).then(function(resp) {
                products = resp.data;
                deferred.resolve(resp.data);
            }, function(err) {
                deferred.reject(err);
            });
        }
	   return deferred.promise;
	};

	factory.reset();
	return factory;
}]);
