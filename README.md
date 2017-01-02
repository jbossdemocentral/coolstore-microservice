Red Hat Cool Store Microservice Demo [![Build Status](https://travis-ci.org/jbossdemocentral/coolstore-microservice.svg?branch=demo-1)](https://travis-ci.org/jbossdemocentral/coolstore-microservice)
====================================
This is an example demo showing a retail store consisting of several of microservices based on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/) and [Node.js](https://access.redhat.com/documentation/en/openshift-enterprise/3.2/paged/using-images/chapter-1-source-to-image-s2i), deployed to [OpenShift](https://access.redhat.com/products/openshift-enterprise-red-hat/).

It demonstrates how to wire up small microservices into a larger application using microservice architectural principals.

Services
--------
There are several individual microservices and infrastructure components that make up this app:

1. Catalog Service - Java application running on [JBoss Web Server (Tomcat)](https://access.redhat.com/products/red-hat-jboss-web-server/) and MongoDB, serves products and prices for retail products
1. Cart Service - Java EE application running on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/), manages shopping cart for each customer
1. Inventory Service - Java EE application running on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/) and PostgreSQL, serves inventory and availability data for retail products
1. Coolstore Gateway - Java EE + Spring Boot + [Camel](http://camel.apache.org) application running on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/), serving as an entry point/router/aggregator to the backend services
1. Web UI - A frontend based on [AngularJS](https://angularjs.org) and [PatternFly](http://patternfly.org) running in a [Node.js](https://access.redhat.com/documentation/en/openshift-container-platform/3.3/paged/using-images/chapter-2-source-to-image-s2i) container.

![Architecture Screenshot](/docs/images/arch-diagram.png?raw=true "Architecture Diagram")

Prerequisites
================
In order to deploy the CoolStore microservices application, you need an OpenShift environment with
* min 8 GB memory quota
* RHEL and JBoss imagestreams installed (check _Troubleshooting_ section for details)

Deploy CoolStore Microservices Application
================
Deploy the CoolStore microservices application using this template `openshift/coolstore-template.yaml`:
```
oc new-project coolstore
oc process -f coolstore-template.yaml | oc create -f -
```

When all pods are deployed, verify all services are functioning:
```
oc rsh $(oc get pods -o name -l application=coolstore-gw)
curl http://catalog-service:8080/api/products
curl http://inventory-service:8080/api/availability/329299
curl http://cart-service:8080/api/cart/FOO
```

Deploy Complete Demo
================
In order to deploy the complete demo infrastructure for demonstrating Microservices, CI/CD, agile integrations and more, use this provisioning script `openshift/scripts/deploy-demo.sh`:
```
$ openshift/scripts/deploy-demo.sh
```

You can delete the demo projects and containers with:
```
$ openshift/scripts/delete-demo.sh
```

Demo Instructions
================
Access the web interface by pointing your browser at the `web-ui` route url.
![Store Screenshot](/../screenshots/screenshots/store.png?raw=true "Store Screenshot")

Notice the UI pods only expose an HTTP endpoint - when users access the UI service through HTTPS,
OpenShift handles the TLS termination at the routing layer.

Troubleshooting
================
* If you see an error like `An error occurred while starting the build.imageStream ...` it might be due to RHEL or JBoss imagestreams not being installed on your OpenShift environment. Contact the OpenShift admin to install these imagestreams with the following commands:

  ```
  oc login -u system:admin
  oc delete -n openshift -f 'https://raw.githubusercontent.com/jboss-openshift/application-templates/master/jboss-image-streams.json'
  oc delete -n openshift -f 'https://raw.githubusercontent.com/openshift/openshift-ansible/master/roles/openshift_examples/files/examples/v1.3/image-streams/image-streams-rhel7.json'
  oc create -n openshift -f 'https://raw.githubusercontent.com/jboss-openshift/application-templates/master/jboss-image-streams.json'
  oc create -n openshift -f 'https://raw.githubusercontent.com/openshift/openshift-ansible/master/roles/openshift_examples/files/examples/v1.3/image-streams/image-streams-rhel7.json'
  ```
* If you attempt to deploy any of the services, and nothing happens, it may just be taking a while to download the Docker builder images. Visit the OpenShift web console and navigate to
Browse->Events and look for errors, and re-run the 'oc delete ; oc create' commands to re-install the images (as outlined at the beginning.)
