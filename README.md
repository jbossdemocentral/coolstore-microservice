Red Hat Cool Store Microservice Demo
====================================
This is an example demo showing a retail store consisting of several of microservices based on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/) and [Node.js](https://access.redhat.com/documentation/en/openshift-enterprise/3.2/paged/using-images/chapter-1-source-to-image-s2i), deployed to [OpenShift](https://access.redhat.com/products/openshift-enterprise-red-hat/) and protected with [Red Hat SSO](https://access.redhat.com/documentation/en/red-hat-single-sign-on/).

It demonstrates how to wire up small microservices into a larger application using microservice architectural principals.

![Store Screenshot](/../screenshots/screenshots/store.png?raw=true "Store Screenshot")

Services
--------
There are several individual microservices and infrastructure components that make up this app:

1. SSO Service - for protecting per-customer RESTful services (such as the cart microservice), using [Red Hat SSO](https://access.redhat.com/documentation/en/red-hat-single-sign-on/)
1. Catalog Service - Java EE application running on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/), serves products and prices for retail products
1. Cart Service - Java EE application running on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/), manages shopping cart for each customer
1. Inventory Service - Java EE application running on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/), serves inventory and availability data for retail products
1. API Gateway - Java EE + Spring Boot + [Camel](http://camel.apache.org) application running on [JBoss EAP 7](https://access.redhat.com/products/red-hat-jboss-enterprise-application-platform/), serving as a protected entry point/router/aggregator to the backend services
1. UI Service - A frontend based on [AngularJS](https://angularjs.org) and [PatternFly](http://patternfly.org) running in a [Node.js](https://access.redhat.com/documentation/en/openshift-enterprise/3.2/paged/using-images/chapter-1-source-to-image-s2i) container.
1. (Optional) Hystrix Dashboard for visualizing microservice performance/metrics
1. (Optional) A [Jenkins](http://jenkins.io) CI/CD server for building the microservices using pipelines

A simple visualization of the runtime components of this demo:

![Architecture Screenshot](/../screenshots/screenshots/arch.png?raw=true "Architecture Screenshot")

Notice the UI pods only expose an HTTP endpoint - when users access the UI service through HTTPS, OpenShift handles the TLS termination at the routing layer.

Demo Credentials and other identifiers
--------------------------------------
1. SSO Realm Name: `myrealm`
1. SSO / JBoss security role name: `user`
1. SSO Admin (used when logging into SSO Admin Console): username `admin` password: `admin`
1. Website User (used when accessing retail store): username: `appuser` password: `password`
1. SSO REST API Admin User (not generally used): username: `ssoservice` password: `ssoservicepass`
1. Jenkins Web Console: username: `admin` password: `password`

Running the Demo
================
Running the demo consists of 5 main steps, one for each of the services listed above.

It is assumed you have installed OpenShift, either using [Red Hat's CDK](http://developers.redhat.com/products/cdk/overview/), `oc cluster up`, or a complete install, and can login to the
web console or use the `oc` CLI tool.

**NOTE:** This step is not required if you are using CDK 2.2 or later, or OCP 3.3 or later.

It is also assumed that the necessary updated JBoss xPaaS ImageStreams are available in the `openshift` namespace. If you have not installed these, you can do so as follows:

    oc delete -n openshift -f 'https://raw.githubusercontent.com/jboss-openshift/application-templates/master/jboss-image-streams.json'
    oc delete -n openshift -f 'https://raw.githubusercontent.com/openshift/openshift-ansible/master/roles/openshift_examples/files/examples/v1.3/image-streams/image-streams-rhel7.json'
    oc create -n openshift -f 'https://raw.githubusercontent.com/jboss-openshift/application-templates/master/jboss-image-streams.json'
    oc create -n openshift -f 'https://raw.githubusercontent.com/openshift/openshift-ansible/master/roles/openshift_examples/files/examples/v1.3/image-streams/image-streams-rhel7.json'

In particular you need the latest `redhat-sso70-openshift`, `jboss-eap70-openshift`, and `nodejs` ImageStream definitions.

Note: SSL/TLS Self-Signed Certificates
--------------------------------------
For demo purposes, you will most likely be using self-signed certificates, which will be apparent when
accessing the services using a browser (you'll get a security warning which must be accepted.)

Note: Using Red Hat OpenShift CDK
---------------------------------
If you intend to do the full demo and are using [Red Hat's Container Development Kit](http://developers.redhat.com/products/cdk/overview/), you will need to edit the `Vagrantfile` to increase the amount of available memory.

You will need to dedicate at least 8GB of memory to the virtual machine. To do this, edit the `Vagrantfile` and modify the following line to read:
```
# Amount of available RAM
VM_MEMORY = ENV['VM_MEMORY'] || 8096
```

Create project and associated service accounts and permissions
--------------------------------------------------------------

In the following steps, substitute your desired project name for OCP_PROJECT, and assume your OpenShift domain is OCP_DOMAIN.
1. Set environment variables for your environment
```
export OCP_PROJECT=coolstore
export OCP_MASTER=10.1.2.2 # hostname or IP of the OpenShift Container Platform Master
export OCP_DOMAIN=rhel-cdk.${OCP_MASTER}.xip.io   # DNS domain that maps to your OpenShift Container Platform master
export MAVEN_MIRROR_URL=http://nexus.ci.svc.cluster.local:8081/repository/maven-public/
```
Note that you many need to change the above to fit your environment, especially if you are NOT using the OpenShift CDK default installation.

If you do not have your own Maven mirror setup, then you don't need to set `MAVEN_MIRROR_URL` but
it is highly recommended to set one up to significantly reduce the build times of the various services. See the bottom of this README for a quick way to do this.

1. Clone this repository
```
git clone https://github.com/jbossdemocentral/coolstore-microservice
cd coolstore-microservice/openshift-templates
```

1. Login and Create a new project
```
oc login https://${OCP_MASTER}:8443
oc new-project ${OCP_PROJECT}
```   

1. Create OpenShift objects for SSL/TLS crypto secrets and associated service account:
```
oc create -f secrets/coolstore-secrets.json
```

1. Add roles to service account to allow for kubernetes clustering access
```
oc policy add-role-to-user view system:serviceaccount:$(oc project -q):default -n $(oc project -q)
oc policy add-role-to-user view system:serviceaccount:$(oc project -q):sso-service-account -n $(oc project -q)
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
oc logs -f bc/sso # watch the build
oc logs -f dc/sso # watch the deployment
```  
It may take some time to pull down the SSO image, if you are on a slow link to the world. Until the image is pulled, you won't be able to monitor the build or deployment. Be patient, please :)

1. Once it completes, you can test it by accessing `https://secure-sso-${OCP_PROJECT}.${OCP_DOMAIN}/auth` or clicking on the associated route from the project overview page within the OpenShift web console. Click on *Administration Console* and login using `admin`/`admin`

1. Obtain the public key for the automatically-created realm `myrealm` by navigating to *Realm Settings* -> *Keys* and set the value to environment variable called `PUBLIC_KEY`.
```
export PUBLIC_KEY=<KEY>
```
or you can retrive it automatically from the command line like this (assuming you have `curl` and `python` installed)
```
export PUBLIC_KEY="$(oc rsh $(oc get pods -o name -l application=sso)  sh -c "curl -sk https://secure-sso.${OCP_PROJECT}.svc.cluster.local:8443/auth/realms/myrealm | python -c \"import sys, json; print json.load(sys.stdin)['public_key']\"")"
```

Deploy API Gateway using the OpenShift `oc` CLI
-----------------------------------------------
The API Gateway relies on the Red Hat SSO xPaaS image for JBoss EAP 7. At runtime, this image will automatically register itself as a *bearer-only* SSO client.
Access to the `/api/products` endpoint does not require authentication. Access to the `/api/cart` endpoint is protected by Red Hat SSO by declaring it to be so in [web.xml](api-gateway/src/main/webapp/WEB-INF/web.xml) by using the [Keycloak REST API](http://www.keycloak.org/docs/rest-api/).

1. Create and deploy service, substituting values for SSO_URL (don't forget the `/auth` suffix) and SSO_PUBLIC_KEY, wait for it to complete.
```
oc process -f api-gateway.json \
    SSO_URL=https://secure-sso-${OCP_PROJECT}.${OCP_DOMAIN}/auth \
    SSO_PUBLIC_KEY=${PUBLIC_KEY} | \
    oc create -f -
```
If you have created a [local Maven mirror](https://blog.openshift.com/improving-build-time-java-builds-openshift/) to speed up your builds, specify it with `MAVEN_MIRROR_URL` in the above command. For example:
```
oc process -f api-gateway.json \
    SSO_URL=https://secure-sso-${OCP_PROJECT}.${OCP_DOMAIN}/auth \
    SSO_PUBLIC_KEY=${PUBLIC_KEY} \
    MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL} | \
    oc create -f -
```

1. Wait for it to complete (this step may take a while as it downloads all Maven dependencies during the build). Follow the logs using
```
oc logs -f bc/api-gateway
```

To confirm successful deployment, visit `https://secure-api-gateway-${OCP_PROJECT}.${OCP_DOMAIN}` in your browser (or click on the link within the OpenShift web console). You should see the swagger API documentation page and you can explore the API.

![Swagger Screenshot](/../screenshots/screenshots/swagger.png?raw=true "Swagger Screenshot")

Deploy Catalog Service using the OpenShift `oc` CLI
---------------------------------------------------
This service relies on the standard xPaaS image for JBoss EAP 7. No route is created to this service, as it is only accessible from inside the kubernetes cluster.

1. Create and deploy service:
```
oc process -f catalog-service.json | oc create -f -
```
If you have created a [local Maven mirror](https://blog.openshift.com/improving-build-time-java-builds-openshift/) to speed up your builds, specify it with `MAVEN_MIRROR_URL` in the above command. For example
```
oc process -f catalog-service.json MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}  | oc create -f -
```

1. Wait for it to complete (this step may take a while as it downloads all Maven dependencies during the build). Follow the logs using
```
oc logs -f bc/catalog-service
```
To confirm this service is reachable from the API Gateway, try to access the service from the API Gateway pod:
```
oc rsh $(oc get pods -o name -l application=api-gateway) curl http://catalog-service:8080/api/products
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
If you have created a [local Maven mirror](https://blog.openshift.com/improving-build-time-java-builds-openshift/) to speed up your builds, specify it with `MAVEN_MIRROR_URL` in the above command. For example:
```
oc process -f inventory-service.json MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL} | oc create -f -
```

1. Wait for it to complete (this step may take a while as it downloads all Maven dependencies during the build). Follow the logs using
```
oc logs -f bc/inventory-service
```
To confirm this service is reachable from the API Gateway, determine the name of the pod running the API Gateway and access the service from the API Gateway pod:
```
oc rsh $(oc get pods -o name -l application=api-gateway) curl http://inventory-service:8080/api/availability/329299
```
You should get a JSON object listing the item ID (foo) and a real availability (quantity and city) e.g.:
```
    {"itemId":"329299","quantity":85,"location":"Frankfurt","link":"http://maps.google.com/?q=frankfurt"}
```

Deploy Cart Service using the OpenShift `oc` CLI
------------------------------------------------
This service relies on the standard xPaaS image for JBoss EAP 7. No route is created to this service, as it is only accessible from inside the kubernetes cluster.

1. Create and deploy service:
```
oc process -f cart-service.json | oc create -f -
```
If you have created a [local Maven mirror](https://blog.openshift.com/improving-build-time-java-builds-openshift/) to speed up your builds, specify it with `MAVEN_MIRROR_URL` in the above command. For example:
```
oc process -f cart-service.json MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL} | oc create -f -
```

1. Wait for it to complete (this step may take a while as it downloads all Maven dependencies during the build). Follow the logs using
```
oc logs -f bc/cart-service
```
To confirm this service is reachable from the API Gateway, determine the name of the pod running the API Gateway and access the service from the API Gateway pod:
```
oc rsh $(oc get pods -o name -l application=api-gateway) curl http://cart-service:8080/api/cart/FOO
```
You should get an empty cart JSON object e.g.:
```
{"cartItemTotal":0.0,"cartItemPromoSavings":0.0,"shippingTotal":0.0,"shippingPromoSavings":0.0,"cartTotal":0.0,"shoppingCartItemList":[]}
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
    SSO_URL=https://secure-sso-${OCP_PROJECT}.${OCP_DOMAIN}/auth \
    SSO_PUBLIC_KEY=${PUBLIC_KEY} \
    HOSTNAME_HTTP=ui-${OCP_PROJECT}.${OCP_DOMAIN} \
    HOSTNAME_HTTPS=secure-ui-${OCP_PROJECT}.${OCP_DOMAIN} \
    API_ENDPOINT=http://api-gateway-${OCP_PROJECT}.${OCP_DOMAIN}/api \
    SECURE_API_ENDPOINT=https://secure-api-gateway-${OCP_PROJECT}.${OCP_DOMAIN}/api | \
    oc create -f -
```

1. Wait for it to complete. You can follow the build logs with
```
oc logs -f bc/ui
```    

Access the Demo
---------------
Once all of the above completes, your demo should be running and you can access the UI using `http://ui-${OCP_PROJECT}.${OCP_DOMAIN}` or the secure variant using `https://secure-ui-${OCP_PROJECT}.${OCP_DOMAIN}`

If you did not generate your own SSL certificates, you will most likely not see the UI initially. You will need to open a separate tab in the same browser and access the other services directly so that you can accept the security exceptions generated from the browser.

1. Visit `https://secure-sso-${OCP_PROJECT}.${OCP_DOMAIN}/` and accept the exception, so that you land on the JBoss EAP landing page.
1. Go back to the demo UI and reload the page. You should now see the login.
1. Click *Login* at the upper-right, and sign into SSO using `appuser`/`password` credentials.
1. If you are accessing the secure variant (e.g. `secure-ui-${OCP_PROJECT}.${OCP_DOMAIN}` then you will also need to visit the secure variant of the API Gateway `https://secure-api-gateway.${OCP_PROJECT}.${OCP_DOMAIN}` to accept the security exception.
1. Once you do all of the above, you should be good to go with the demo. If not, see the Troubleshooting section below.

You can log into the store using username `appuser` and password `password`. You will be prompted to change your password upon first login.

You can then add products, and click on *Shopping Cart* to see the pricing and ability to checkout.

Optional: Install [Kubeflix](https://github.com/fabric8io/kubeflix) (Hystrix Dashboard and Turbine server for metrics reporting on the services)
------------------------------------------------------------------------------------------------------------------------------------------------
This service provides [Kubernetes](http://kubernetes.io/) integration with [Netflix](https://netflix.github.io/) open source components such as [Hystrix](https://github.com/Netflix/Hystrix), [Turbine](https://github.com/Netflix/Turbine) and [Ribbon](https://github.com/Netflix/Ribbon).

Turbine is meant to discover and aggregate Hystrix metrics streams, so that its possible to extract and display meaningful information (e.g. display the aggregate stream on the dashboard).

To install:
```
oc adm policy add-scc-to-user anyuid -z ribbon
oc create -f http://central.maven.org/maven2/io/fabric8/kubeflix/packages/kubeflix/1.0.17/kubeflix-1.0.17-kubernetes.yml
oc new-app kubeflix
oc expose service hystrix-dashboard
oc patch route hystrix-dashboard -p '{"spec": { "port": { "targetPort": 8080 } } }'
oc policy add-role-to-user admin system:serviceaccount:$(oc project -q):turbine
```
To monitor the progress of the build, run
```
    oc logs -f rc/hystrix-dashboard
```

Once installed, Visit `http://hystrix-dashboard-${OCP_PROJECT}.${OCP_DOMAIN}` and click *Monitor Stream*. In a separate window, as you access the demo, you can see the load on the various services and whether their circuits are open.

Optional: Install Jenkins (For CI/CD pipeline builds of each microservice)
--------------------------------------------------------------------------
This service installs a [Jenkins](https://jenkins.io/) server, configured to build three of the microservices in a pipeline. It will create `-dev` and `-qa` OpenShift projects in
which the services are built, and wait for approval before deploying to the production environment specified with `PROD_${OCP_PROJECT}`.

You can install it into any project, including the "production" project you've been using up to this point, although typically it is installed in a separate
project (often called `ci`, and that is what we'll use below):

To install:
```
oc new-project ci
oc policy add-role-to-user edit system:serviceaccount:ci:default -n ci
oc process -f jenkins.json PROD_PROJECT=${OCP_PROJECT} MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL} | oc create -f -
```
(You can also specify `MAVEN_MIRROR_URL=<url>` above if you have a local maven mirror to speed up the build(s).

Once installed, visit the Jenkins dashboard by clicking on the route name in the OpenShift overview. You can click on each of the *Jobs* and then click *Build with Parameters* to fire off the build.

Each microservice is independently built in a `-dev` project, then unit tests are simulated, then the build is promoted (via `oc tag`) to the `-qa` project. Finally, you are prompted to accept or reject. If you accept, the build
is then promoted (again, using `oc tag`) to the production environment specified with `PROD_PROJECT`.

You can read [more information about Jenkins Pipelines](https://jenkins.io/doc/pipeline/).

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
* If you see *Client not found.* on the SSO login page, may need to scale the UI service down to 0 and back to 1, to force a re-registration of the client into Red Hat SSO.
* If you get an *Error! Error retrieving products* when accessing the secure UI frontend, this is due to the use of a different self-signed certificates by OpenShift when the UI tries to access the secured API gateway.
You can do one of two things:
    * Use the insecure UI in your browser, i.e. go to http://ui-${OCP_PROJECT}.${OCP_DOMAIN}
    * Visit https://secure-api-gateway-${OCP_PROJECT}.${OCP_DOMAIN} in a separate browser tab, accept the security exception (and ignore the *Unauthorized* error you may see), then return to the original tab and reload the page.
* If you stop and restart the SSO service, this will change the value for `SSO_PUBLIC_KEY` so you'll need to reconfigure the API Gateway and UI services to reflect this:
```
export PUBLIC_KEY="$(oc rsh $(oc get pods -o name -l application=sso)  sh -c "curl -sk https://secure-sso.${OCP_PROJECT}.svc.cluster.local:8443/auth/realms/myrealm | python -c \"import sys, json; print json.load(sys.stdin)['public_key']\"")"
oc set env dc/api-gateway SSO_PUBLIC_KEY="${PUBLIC_KEY}"
oc set env dc/ui SSO_PUBLIC_KEY="${PUBLIC_KEY}"
```
This should cause the Api Gateway and UI pods to be re-deployed with the updated configuration.

Setting up a local Maven mirror
-------------------------------
A local Maven mirror will cache copies of Maven dependencies so that your builds can use the local copy, speeding up the build significantly. To set one up:

```
oc new-project ci
oc new-app --name nexus sonatype/nexus3
oc expose svc nexus
oc project ${OCP_PROJECT}    # don't forget to switch back to your work-in-progress projects for future oc commands!
```
Monitor the deployment with `oc logs -n ci -f dc/nexus`. Once the deployment completes, visit `http://nexus-ci.${OCP_DOMAIN}` and login with username:`admin` password:`admin123`.

Next, Add the Red Hat GA repository as an additional mirrored repository using these steps:

1. Click the "Gear" icon at the top to enter the administrative section. 
1. Click *Create Repository* -> *maven2 (proxy)*
1. Fill out these fields (leave the others blank): Name: `redhat-ga`, Remote Storage URL: `https://maven.repository.redhat.com/ga/`, Blob Store: Select `default`
1. Click *Create Repository* at the bottom
1. Back on the repository list screen, click on `maven-public`
1. Scroll to bottom, click on `redhat-ga` in the left box to select it, then move it from the left to the right box, using the right arrow button (>).
1. Click *Save*

You can now configure `MAVEN_MIRROR_URL` using either the external URL that is shown at the top of the repository configuration page (e.g. `http://nexus-ci.${OCP_DOMAIN}/repository/maven-public/` OR the
kubernetes internal URL `http://nexus.ci.svc.cluster.local:8081/repository/maven-public/` (this is what is used in the environment setup at the top of this document).

Notes
-----
* You can optionally install the 3 templates into OpenShift for use via the GUI using `oc create -f openshift-templates -n openshift`. Once created, you can then deploy the services in your projects using *Add To Project*
