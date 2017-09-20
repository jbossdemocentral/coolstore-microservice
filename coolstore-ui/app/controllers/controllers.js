'use strict';

angular.module('app')

    .controller("HomeController",
        ['$scope', '$http', '$filter', '$modal', 'Notifications', 'cart', 'catalog', 'Auth', 'Rating',
            function ($scope, $http, $filter, $modal, Notifications, cart, catalog, $auth, Rating) {

                $scope.products = [];
                $scope.addToCart = function (item) {
                    cart.addToCart(item.product, parseInt(item.quantity)).then(function (data) {
                        Notifications.success("Added! Your total is " + $filter('currency')(data.cartTotal));
                    }, function (err) {
                        Notifications.error("Error adding to cart: " + err.statusText);
                    });
                };

                $scope.isLoggedIn = function () {
                    return $auth.loggedIn;
                };
                $scope.ssoEnabled = function () {
                    return $auth.ssoEnabled;
                };

                $scope.login = function () {
                    $auth.login();
                };

                $scope.rateFunction = function(itemId, rating)
                {
                    Rating.postRating(itemId, rating).then(function(newRate) {
                        $scope.products.forEach(function(item) {
                            if (item.product.itemId === itemId) {
                                item.product.rating.rating = rating;
                                item.product.rating.rated = true;
                                item.product.rating.count++;
                            }
                        });
                    }, function(err) {
                        Notifications.error("Error saving rating: " + err.statusText);
                    });
                };

                $scope.showReviews = function(product) {
                    $modal.open({
                        templateUrl: 'partials/reviews.html',
                        controller: 'ReviewController',
                        size: 'lg',
                        resolve: {
                            product: function () {
                                return product
                            }

                        }
                    });
                };

                // initialize products
                catalog.getProducts().then(function (data) {
                    if (data.error != undefined && data.error != "") {
                        Notifications.error("Error retrieving products: " + data.error);
                        return;
                    }
                    
                    $scope.products = data.map(function (el) {
                        return {
                            quantity: "1",
                            product: el
                        }
                    })
                }, function (err) {
                    Notifications.error("Error retrieving products: " + err.statusText);
                });


            }])

    .controller("ReviewController",
        ['$scope', '$http', 'Notifications', 'product', 'review',
            function ($scope, $http, Notifications, product, review) {

                $scope.product = product;
                $scope.reviews = null;

                review.getReviews(product.itemId).then(function(reviews) {
                    $scope.reviews = reviews;
                }, function(err) {
                    Notifications.error("Error fetching reviews for " + product.name + ": " + err.statusText);
                });
            }])

    .controller("CartController",
        ['$scope', '$http', 'Notifications', 'cart', 'Auth',
            function ($scope, $http, Notifications, cart, $auth) {

                function reset() {
                    $scope.cart = cart.getCart();
                    $scope.items = $scope.cart.shoppingCartItemList;

                    $scope.subtotal = 0;
                    $scope.cart.shoppingCartItemList.forEach(function (item) {
                        $scope.subtotal += (item.quantity * item.product.price);
                    });
                }

                $scope.config = {
                    selectItems: false,
                    multiSelect: false,
                    dblClick: false,
                    showSelectBox: false
                };

                function performAction(action, item) {
                    cart.removeFromCart(item.product, item.quantity).then(function (newCart) {
                        reset();
                    }, function (err) {
                        Notifications.error("Error removing from cart: " + err.statusText);
                    });
                };

                $scope.actionButtons = [
                    {
                        name: 'Remove',
                        title: 'Remove',
                        actionFn: performAction
                    }
                ];


                $scope.$watch(function () {
                    return cart.getCart();
                }, function (newValue) {
                    reset();
                });

                $scope.$watch(function () {
                    return $auth.userInfo;
                }, function (newValue) {
                    cart.reset();
                });

                $scope.checkout = function () {
                    cart.checkout().then(function (cartData) {
                    }, function (err) {
                        Notifications.error("Error checking out: " + err.statusText);
                    });
                };

                $scope.isLoggedIn = function () {
                    return $auth.loggedIn;
                };
                $scope.ssoEnabled = function () {
                    return $auth.ssoEnabled;
                };

                reset();
            }])

    .controller("HeaderController",
        ['$scope', '$location', '$http', 'Notifications', 'cart', 'Auth',
            function ($scope, $location, $http, Notifications, cart, $auth) {
                $scope.userInfo = $auth.userInfo;

                $scope.cartTotal = 0.0;
                $scope.itemCount = 0;

                $scope.isLoggedIn = function () {
                    return $auth.loggedIn;
                };

                $scope.login = function () {
                    $auth.login();
                };
                $scope.logout = function () {
                    $auth.logout();
                };
                $scope.isLoggedIn = function () {
                    return $auth.loggedIn;
                };
                $scope.ssoEnabled = function () {
                    return $auth.ssoEnabled;
                };
                $scope.profile = function () {
                    $auth.authz.accountManagement();
                };
                $scope.$watch(function () {
                    return cart.getCart().cartTotal || 0.0;
                }, function (newValue) {
                    $scope.cartTotal = newValue;
                });

                $scope.$watch(function () {
                    var totalItems = 0;
                    cart.getCart().shoppingCartItemList.forEach(function (el) {
                        totalItems += el.quantity;
                    });
                    return totalItems;
                }, function (newValue) {
                    $scope.itemCount = newValue;
                });

                $scope.$watch(function () {
                    return $auth.userInfo;
                }, function (newValue) {
                    $scope.userInfo = newValue;
                });

                $scope.isActive = function (loc) {
                    return loc === $location.path();
                }
            }]);
