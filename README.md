Red Hat Cool Store Microservice Demo [![Build Status](https://travis-ci.org/jbossdemocentral/coolstore-microservice.svg?branch=1.1.x)](https://travis-ci.org/jbossdemocentral/coolstore-microservice)
====================================
This is an example demo showing a retail store consisting of several microservices based on [Red Hat OpenShift Application Runtimes](https://www.redhat.com/en/resources/openshift-application-runtimes-datasheet) (Spring Boot, WildFly Swarm, Vert.x, JBoss EAP and Node.js) deployed to [OpenShift](https://access.redhat.com/documentation/en/openshift-container-platform).

It demonstrates how to wire up small microservices into a larger application using microservice architectural principals.

Development Branch
------------------
:warning: **Please note that master is our development branch and may contain untested features.** For stable branch use a version branch like `1.1.x`

Services
--------
There are several individual microservices and infrastructure components that make up this app:

1. Catalog Service - Java application running on [JBoss Web Server (Tomcat)](https://access.redhat.com/products/red-hat-jboss-web-server/) and MongoDB, serves products and prices for retail products
1. Cart Service - Spring Boot application running on JDK which manages shopping cart for each customer
1. Inventory Service - Java EE application running on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/) and PostgreSQL, serves inventory and availability data for retail products
1. Pricing Service - Business rules application for product pricing on [JBoss BRMS](https://www.redhat.com/en/technologies/jboss-middleware/business-rules)
1. Review Service - WildFly Swarm service running on JDK for writing and displaying reviews for products
1. Rating Service - Vert.x service running on JDK for rating products
1. Coolstore Gateway - Spring Boot + [Camel](http://camel.apache.org) application running on JDK serving as an API gateway to the backend services
1. Web UI - A frontend based on [AngularJS](https://angularjs.org) and [PatternFly](http://patternfly.org) running in a [Node.js](https://access.redhat.com/documentation/en/openshift-container-platform/3.3/paged/using-images/chapter-2-source-to-image-s2i) container.

![Architecture Screenshot](docs/images/arch-diagram.png?raw=true "Architecture Diagram")

![Architecture Screenshot](docs/images/store.png?raw=true "CoolStore Online Shop")

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
curl http://rating:8080/api/rating/329299
curl http://review:8080/api/review/329299
```

Deploy CoolStore Microservices with CI/CD
================
In order to deploy the complete demo infrastructure for demonstrating Microservices, CI/CD, 
agile integrations and more, either order the demo via RHPDS or use the following script to provision the demo
on any OpenShift environment:

```
$ oc login MASTER-URL
$ openshift/scripts/provision-demo.sh deploy msa-cicd-eap
```

You can delete the demo projects and containers with:
```
$ openshift/scripts/provision-demo.sh delete msa-cicd-eap
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
  oc delete -n openshift -f 'https://raw.githubusercontent.com/jboss-fuse/application-templates/GA/fis-image-streams.json'
  oc create -n openshift -f 'https://raw.githubusercontent.com/jboss-openshift/application-templates/master/jboss-image-streams.json'
  oc create -n openshift -f 'https://raw.githubusercontent.com/openshift/openshift-ansible/master/roles/openshift_examples/files/examples/v1.3/image-streams/image-streams-rhel7.json'
  oc create -n openshift -f 'https://raw.githubusercontent.com/jboss-fuse/application-templates/GA/fis-image-streams.json'
  ```
* If you attempt to deploy any of the services, and nothing happens, it may just be taking a while to download the Docker builder images. Visit the OpenShift web console and navigate to
Browse->Events and look for errors, and re-run the 'oc delete ; oc create' commands to re-install the images (as outlined at the beginning.)
