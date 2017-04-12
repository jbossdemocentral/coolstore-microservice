Red Hat Cool Store Microservice Demo [![Build Status](https://travis-ci.org/jbossdemocentral/coolstore-microservice.svg?branch=demo-1)](https://travis-ci.org/jbossdemocentral/coolstore-microservice)
====================================
This is an example demo showing a retail store consisting of several of microservices based on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/) and [Node.js](https://access.redhat.com/documentation/en-us/openshift_container_platform/3.4/html/using_images/source-to-image-s2i), deployed to [OpenShift](https://access.redhat.com/documentation/en/openshift-container-platform//).

It demonstrates how to wire up small microservices into a larger application using microservice architectural principals.

Services
--------
There are several individual microservices and infrastructure components that make up this app:

1. Catalog Service - Java application running on [JBoss Web Server (Tomcat)](https://access.redhat.com/products/red-hat-jboss-web-server/) and MongoDB, serves products and prices for retail products
1. Cart Service - Spring Boot application, manages shopping cart for each customer
1. Inventory Service - Java EE application running on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/) and PostgreSQL, serves inventory and availability data for retail products
1. Coolstore Gateway - Java EE + Spring Boot + [Camel](http://camel.apache.org) application running on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/), serving as an entry point/router/aggregator to the backend services
1. Web UI - A frontend based on [AngularJS](https://angularjs.org) and [PatternFly](http://patternfly.org) running in a [Node.js](https://access.redhat.com/documentation/en/openshift-container-platform/3.3/paged/using-images/chapter-2-source-to-image-s2i) container.
1. Pricing - Business rules application for product pricing on [JBoss BRMS](https://www.redhat.com/en/technologies/jboss-middleware/business-rules)

![Architecture Screenshot](docs/images/arch-diagram.png?raw=true "Architecture Diagram")

<img src="docs/images/store.png?raw=true" width="740" />

Prerequisites
================
In order to deploy the CoolStore microservices application, you need an OpenShift environment with
* 4+ GB memory quota if deploying CoolStore
* 16+ GB memory quota if deploying the [complete demo](openshift/scripts)
* RHEL and JBoss imagestreams installed (check _Troubleshooting_ section for details)
* Nexus Repository (or other maven repository managers) with [proxy repositories](https://books.sonatype.com/nexus-book/reference/confignx-sect-manage-repo.html) defined for [JBoss Enterprise Maven Repository](https://access.redhat.com/maven-repository)

Deploy CoolStore Microservices Application
================
Deploy the CoolStore microservices application using this template `openshift/coolstore-template.yaml`:
```
oc login -u developer
oc new-project coolstore
oc process -f openshift/coolstore-template.yaml | oc create -f -
```

When all pods are deployed, verify all services are functioning:
```
oc rsh $(oc get pods -o name -l application=coolstore-gw)
curl http://catalog:8080/api/products
curl http://inventory:8080/api/availability/329299
curl http://cart:8080/api/cart/FOO
```

Deploy Complete Demo with CI/CD
================
In order to deploy the complete demo infrastructure for demonstrating Microservices, CI/CD, 
agile integrations and more, either order the demo via RHPDS or use the following script to provision the demo
on any OpenShift environment:

```
$ oc login MASTER-URL
$ openshift/scripts/provision-demo.sh 
```

You can delete the demo projects and containers with:
```
$ openshift/scripts/provision-demo.sh --delete
```

![CI/CD Demo](docs/images/cicd-projects.png?raw=true)
![CI/CD Demo](docs/images/cicd-pipeline.png?raw=true)

Read the [script docs](openshift/scripts) for further details and how to run the demo on a local cluster with `oc cluster`.

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
