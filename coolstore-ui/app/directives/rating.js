'use strict';

angular.module('app').directive('starRating',
    function() {
        return {
            restrict : 'A',
            template : '<ul class="rating"><li ng-repeat="star in stars" ng-class="star" ng-click="toggle($index)">'
                + '<i class="fa fa-star"></i>'
                + '</li>'
                + '</ul>',
            scope : {
                ratingValue : '=',
				itemId: '=',
                max : '=',
                onRatingSelected : '&'
            },
            link : function(scope, elem, attrs) {
                var updateStars = function() {
                    scope.stars = [];
                    for ( var i = 0; i < scope.max; i++) {
                        scope.stars.push({
                            filled : i < scope.ratingValue,
                            rateable: scope.itemId !== undefined && scope.itemId !== ''
                        });
                    }
                };

                scope.toggle = function(index) {
                    scope.ratingValue = index + 1;
                    scope.onRatingSelected({
                        itemId: scope.itemId,
                        rating: index + 1
                    });
                };

                scope.$watch('ratingValue',
                    function(oldVal, newVal) {
                        if (newVal) {
                            updateStars();
                        }
                    }
                );
            }
        };
    }
);
