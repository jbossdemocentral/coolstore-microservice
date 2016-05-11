'use strict';


angular.module('app').directive('header', function() {
	return {
		restrict: 'A',
		replace: true,
		templateUrl: 'partials/header.html',
		controller: 'HeaderController'
	}
});
