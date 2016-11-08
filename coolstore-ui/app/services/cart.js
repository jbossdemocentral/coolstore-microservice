'use strict';

angular.module("app")

.factory('cart', ['$http', '$q', 'COOLSTORE_CONFIG', 'Auth', '$location', function($http, $q, COOLSTORE_CONFIG, $auth, $location) {
	var factory = {}, cart, products, cartId;

	factory.checkout = function() {
		var deferred = $q.defer();
		$http({
			   method: 'POST',
			   url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_API_ENDPOINT : COOLSTORE_CONFIG.API_ENDPOINT) + '/cart/checkout/' + cartId
		   }).then(function(resp) {
			    cart = resp.data;
			   	deferred.resolve(resp.data);
		   }, function(err) {
			   	deferred.reject(err);
		   });
		return deferred.promise;
	};

	factory.reset = function() {
		cart = {
			shoppingCartItemList: []
		};
		var tmpId = localStorage.getItem('cartId');
		var authId = $auth.userInfo ? $auth.userInfo.sub : null;

		if (tmpId && authId) {
			// transfer cart
			cartId = authId;
			this.setCart(tmpId).then(function(result) {
				localStorage.removeItem('cartId');
			}, function(err) {
				console.log("could not transfer cart " + tmpId + " to cart " +  authId + ": " + err);
			});
			return;
		}

		if (tmpId && !authId) {
			cartId = tmpId;
		}

		if (!tmpId && authId) {
			cartId = authId;
		}

		if (!tmpId && !authId) {
			tmpId = 'id-' + Math.random();
			localStorage.setItem('cartId', tmpId);
			cartId = tmpId;
		}

		cart.shoppingCartItemList = [];
		$http({
			   method: 'GET',
			   url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_API_ENDPOINT : COOLSTORE_CONFIG.API_ENDPOINT) + '/cart/' + cartId
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
			url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_API_ENDPOINT : COOLSTORE_CONFIG.API_ENDPOINT) + '/cart/' + cartId + '/' + product.itemId + '/' + quantity
		}).then(function(resp) {
			cart = resp.data;
			deferred.resolve(resp.data);
		}, function(err) {
			deferred.reject(err);
		});
		return deferred.promise;

	};

	factory.setCart = function(id) {
		var deferred = $q.defer();
		$http({
			method: 'POST',
			url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_API_ENDPOINT : COOLSTORE_CONFIG.API_ENDPOINT) + '/cart/' + cartId + '/' + id
		}).then(function(resp) {
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
			   url: ($location.protocol() === 'https' ? COOLSTORE_CONFIG.SECURE_API_ENDPOINT : COOLSTORE_CONFIG.API_ENDPOINT) + '/cart/' + cartId + '/' + product.itemId + '/' + quantity
		   }).then(function(resp) {
			    cart = resp.data;
			   	deferred.resolve(resp.data);
		   }, function(err) {
			   	deferred.reject(err);
		   });
		return deferred.promise;

	};
	
	factory.reset();
	return factory;
}]);
