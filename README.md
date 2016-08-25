Red Hat Cool Store Microservice Demo
====================================
This is an example demo showing a retail store consisting of several of microservices based on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/) and [Node.js](https://access.redhat.com/documentation/en/openshift-enterprise/3.2/paged/using-images/chapter-1-source-to-image-s2i), deployed to [OpenShift](https://access.redhat.com/products/openshift-enterprise-red-hat/) and protected with [Red Hat SSO](https://access.redhat.com/documentation/en/red-hat-single-sign-on/).

It demonstrates how to wire up small microservices into a larger application using microservice architectural principals.

![Store Screenshot](/../screenshots/screenshots/store.png?raw=true "Store Screenshot")

Services
--------
There are several individual microservices that make up this app:

1. SSO Service - for protecting RESTful pricing service, using [Red Hat SSO](https://access.redhat.com/documentation/en/red-hat-single-sign-on/)
1. Pricing Service - Java EE application running on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/), serves products and prices for retail products
1. Inventory Service - Java EE application running on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/), serves inventory and availability data for retail products
1. API Gateway - Java EE + Spring Boot application running on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/), serving as a protected entry point/router/aggregator to the backend services
1. UI Service - A frontend based on [AngularJS](https://angularjs.org) and [PatternFly](http://patternfly.org) running in a [Node.js](https://access.redhat.com/documentation/en/openshift-enterprise/3.2/paged/using-images/chapter-1-source-to-image-s2i) container.

A simple visualization of the runtime components of this demo:

![Architecture Screenshot](/../screenshots/screenshots/arch.jpg?raw=true "Architecture Screenshot")

Notice the UI pods only expose an HTTP endpoint - when users access the UI service through HTTPS, OpenShift handles the TLS termination at the routing layer. The other pods expose both HTTP and HTTPS endpoints and are exposed directly to the browser (necessary for SSO login/logout and AJAX calls to the pricing service)


Demo Credentials and other identifiers
--------------------------------------
1. SSO Realm Name: `myrealm`
1. SSO / JBoss security role name: `user`
1. SSO Admin (used when logging into SSO Admin Console): username `admin` password: `admin`
1. Website User (used when accessing retail store): username: `appuser` password: `password`
1. SSO REST API Admin User (not generally used): username: `ssoservice` password: `ssoservicepass`

Running the Demo
================
Running the demo consists of 5 main steps, one for each of the services listed above. 

It is assumed you have installed OpenShift, either using [Red Hat's CDK](http://developers.redhat.com/products/cdk/overview/) or a complete install, and can login to the
web console or use the `oc` CLI tool.

It is also assumed that the necessary updated JBoss xPaaS ImageStreams are available in the `openshift` namespace. If you have not installed these, you can do so as follows:

    $ oc delete -n openshift -f 'https://raw.githubusercontent.com/jboss-openshift/application-templates/master/jboss-image-streams.json'
    $ oc delete -n openshift -f 'https://raw.githubusercontent.com/openshift/openshift-ansible/master/roles/openshift_examples/files/examples/v1.3/image-streams/image-streams-rhel7.json'
    $ oc create -n openshift -f 'https://raw.githubusercontent.com/jboss-openshift/application-templates/master/jboss-image-streams.json'
    $ oc create -n openshift -f 'https://raw.githubusercontent.com/openshift/openshift-ansible/master/roles/openshift_examples/files/examples/v1.3/image-streams/image-streams-rhel7.json'
    
In particular you need the latest `redhat-sso70-openshift`, `jboss-eap70-openshift`, and `nodejs` ImageStream definitions.

Note: SSL/TLS Self-Signed Certificates
--------------------------------------
For demo purposes, you will most likely be using self-signed certificates, which will be apparent when
accessing the services using a browser (you'll get a security warning which must be accepted.)

Create project and associated service accounts and permissions
--------------------------------------------------------------

In the following steps, substitute your desired project name for PROJECT, and assume your OpenShift domain is DOMAIN.

1. Clone this repository
```
    $ git clone https://github.com/jamesfalkner/coolstore-microservice
    $ cd coolstore-microservice/openshift-templates
```

1. Login and Create a new project
```
    $ oc login https://DOMAIN:8443
    $ oc new-project PROJECT
```   

1. Create OpenShift objects for SSL/TLS crypto secrets and associated service account:
```
    $ oc create -f secrets/coolstore-secrets.json
```

1. Add roles to service account to allow for kubernetes clustering access
```
    $ oc policy add-role-to-user view system:serviceaccount:PROJECT:default -n PROJECT
    $ oc policy add-role-to-user view system:serviceaccount:PROJECT:sso-service-account -n PROJECT
```

Deploy SSO service on OpenShift using the OpenShift `oc` CLI
------------------------------------------------------------

This will build and deploy an instance of Red Hat SSO server (based on [Keycloak](https://keycloak.org)) using
Red Hat's [xPaaS SSO image](https://access.redhat.com/documentation/en/red-hat-xpaas/version-0/red-hat-xpaas-sso-image/).

During the deployment of the SSO service, a new SSO realm `myrealm` is created, along with the administrative users necessary for accessing the SSO REST interface later on.

1. Create and deploy SSO service, wait for it to complete.
```
    oc process -f sso-service.json | oc create -f -
```
You can view the process of the deployment using:
```    
    oc logs -f dc/sso
```  
1. Once it completes, you can test it by accessing `https://secure-sso-PROJECT.DOMAIN/auth` or clicking on the associated route from the project overview page within the OpenShift web console. Click on *Administration Console* and login using `admin`/`admin`

1. Obtain the public key for the automatically-created realm `myrealm` by navigating to *Realm Settings* -> *Keys*. You'll need in the next steps.

Deploy API Gateway using the OpenShift `oc` CLI
-----------------------------------------------
The API Gateway relies on the Red Hat SSO xPaaS image for JBoss EAP 7. At runtime, this image will automatically register itself as a *bearer-only* SSO client.
Access to the `/api` endpoint is protected by Red Hat SSO by declaring it to be so in [web.xml](api-gateway/src/main/webapp/WEB-INF/web.xml) by using the [Keycloak REST API](http://www.keycloak.org/docs/rest-api/).

1. Create and deploy service, substituting values for SSO_URL (don't forget the `/auth` suffix) and SSO_PUBLIC_KEY, wait for it to complete. 
```
    oc process -f api-gateway.json \
      SSO_URL=https://secure-sso-PROJECT.DOMAIN/auth \
      SSO_PUBLIC_KEY=<PUBLIC_KEY> | \
      oc create -f -
```
If you have created a [local Maven mirror](https://blog.openshift.com/improving-build-time-java-builds-openshift/) to speed up your builds, specify it with `MAVEN_MIRROR_URL` in the above command. 

1. Wait for it to complete (this step may take a while as it downloads all Maven dependencies during the build). Follow the logs using
```
    oc logs -f bc/api-gateway
``` 

To confirm successful deployment, visit `https://secure-api-gateway-PROJECT.DOMAIN` in your browser (or click on the link within the OpenShift web console). You should see the pricing service API documentation page and you can explore the API.

![Swagger Screenshot](/../screenshots/screenshots/swagger.png?raw=true "Swagger Screenshot")

Deploy Pricing Service using the OpenShift `oc` CLI
---------------------------------------------------
This service relies on the standard xPaaS image for JBoss EAP 7. No route is created to this service, as it is only accessible from inside the kubernetes cluster.

1. Create and deploy service: 
```
    oc process -f pricing-service.json | oc create -f -
```
If you have created a [local Maven mirror](https://blog.openshift.com/improving-build-time-java-builds-openshift/) to speed up your builds, specify it with `MAVEN_MIRROR_URL` in the above command. 

1. Wait for it to complete (this step may take a while as it downloads all Maven dependencies during the build). Follow the logs using
```
    oc logs -f bc/pricing
``` 

To confirm this service is reachable from the API Gateway, determine the name of the pod running the API Gateway and access the service from the API Gateway pod:
```
    $ oc get pods
    $ oc rsh [API-GATEWAY-POD-NAME] curl http://pricing-service:8080/api/products/list
```

You should get a JSON object listing the products along with invalid inventory (since you haven't deployed the inventory service yet.) e.g.:

```
[{"itemId":"329299","name":"Red Fedora","desc":"Official Red Hat Fedora","price":34.99}, ... ]
```

Deploy Inventory Service using the OpenShift `oc` CLI
-----------------------------------------------------
This service relies on the standard xPaaS image for JBoss EAP 7. No route is created to this service, as it is only accessible from inside the kubernetes cluster.

1. Create and deploy service: 
```
    oc process -f inventory-service.json | oc create -f -
```
If you have created a [local Maven mirror](https://blog.openshift.com/improving-build-time-java-builds-openshift/) to speed up your builds, specify it with `MAVEN_MIRROR_URL` in the above command. 

1. Wait for it to complete (this step may take a while as it downloads all Maven dependencies during the build). Follow the logs using
```
    oc logs -f bc/inventory
``` 

To confirm this service is reachable from the API Gateway, determine the name of the pod running the API Gateway and access the service from the API Gateway pod:
```
    $ oc get pods
    $ oc rsh [API-GATEWAY-POD-NAME] curl http://inventory-service:8080/api/availability/329299
```

You should get a JSON object listing the item ID (foo) and a real availability (quantity and city) e.g.:

```
    {"itemId":"1234","availability":"36 available at Raleign store!"}
```

Deploy the UI Service using the OpenShift `oc` CLI
--------------------------------------------------
This service is implemented as a Node.js runtime with embedded HTTP server. At runtime, the image will automatically register several items into the SSO service:

* A *public* SSO client within realm `myrealm` and valid redirects back to the UI (for redirecting to/from the SSO login page)
* A realm-level role named `user`
* A User named `appuser` that has been granted the `user` role.

1. Create and deploy service, substituting the appropriate values for the various services:
```
    oc process -f ui-service.json \
      SSO_URL=https://secure-sso-PROJECT.DOMAIN/auth \
      SSO_PUBLIC_KEY='<PUBLIC KEY>' \
      HOSTNAME_HTTP=ui-PROJECT.DOMAIN \
      HOSTNAME_HTTPS=secure-ui-PROJECT.DOMAIN \
      API_ENDPOINT=http://api-gateway-PROJECT.DOMAIN/api \
      SECURE_API_ENDPOINT=https://secure-api-gateway-PROJECT.DOMAIN/api | \
      oc create -f -
```

1. Wait for it to complete. You can follow the build logs with
```
    oc logs -f bc/ui
```    

Access the Demo
---------------
Once all of the above completes, your demo should be running and you can access the UI using `http://ui-PROJECT.DOMAIN` or the secure variant using `https://secure-ui-PROJECT.DOMAIN` 

You can also click the links to the various services within the OpenShift web console by navigating to your newly-created project.

You can log into the store using username `appuser` and password `password`. You will be prompted to change your password upon first login.

Troubleshooting
---------------
* If you attempt to deploy any of the services, and nothing happens, it may just be taking a while to download the Docker builder images. Visit the OpenShift web console and navigate to
Browse->Events and look for errors, and re-run the 'oc delete ; oc create' commands to re-install the images (as outlined at the beginning.)
* If you access the demo, and are redirected to the Red Hat SSO login page with an error *invalid redirect uri* - this means that the UI deployment failed to register itself and the `appuser` user into Red Hat SSO. Run `oc logs dc/sso` and verify that
you see successful creation of users and roles. For example look for messages like *Fetch user 'appuser' result: 200 OK*. If you see errors (or you see nothing) chances are you specified an incorrect value for one of the `SSO_URL`, `SSO_PUBLIC_KEY`, `HOSTNAME_HTTP` or `HOSTNAME_HTTPS` (note the last two are hostnames only, not prefixed with `http` or `https`). Verify that you are using the correct URLs, and if you need to fix them:
```
oc set env dc/sso SSO_URL=<the right one> SSO_PUBLIC_KEY=<the right one> HOSTNAME_HTTP=<the right one> HOSTNAME_HTTPS=<the right one>
```
Once fixed, the UI should automatically re-build and re-deploy.
* If you get an *Error! Error retrieving products* when accessing the secure UI frontend, this is due to the use of a different self-signed certificates by OpenShift when the UI tries to access the secured API gateway.
You can do one of two things:
    * Use the insecure UI in your browser, i.e. go to http://ui-PROJECT.DOMAIN 
    * Visit https://secure-api-gateway-PROJECT.DOMAIN in a separate browser tab, accept the security exception (and ignore the *Unauthorized* error you may see), then return to the original tab and reload the page.

Notes
-----
* You can optionally install the 3 templates into OpenShift for use via the GUI using `oc create -f openshift-templates -n openshift`. Once created, you can then deploy the services in your projects using *Add To Project* 



