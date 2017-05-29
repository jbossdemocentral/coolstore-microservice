Enable Single Sign-On for the Coolstore Demo
============================================
You must have the coolstore demo running 

Follow below instructions to enable SSO manually.

1. If you don't already have a CI project create one otherwise make sure to set variable `PRJ_CI` to the name of the project where to install the SSO server.
        
        oc new-project ci
        PRJ_CI=ci

1. Set variable `PRJ_COOLSTORE_PROD` to the name of the project where the coolstore demo that you want to secure is running

        PRJ_COOLSTORE_PROD=coolstore

1. Set environment variable `DOMAIN` matching the application suffix e.g. apps.example.com

        DOMAIN=apps.example.com

1. Get the hostname of the route to the web ui

        WEB_UI_HOSTNAME=$(oc get route web-ui --template='{{.spec.host}}' -n ${PRJ_COOLSTORE_PROD})

1. Install the SSO Server (Keycloak)

        oc process -f openshift/templates/coolstore-sso-template.yaml -p WEB_UI_HOSTNAME=${WEB_UI_HOSTNAME} | oc create -n $PRJ_CI -f -

1. Wait for the SSO server to start

1. Add environment variables to the Web UI deployment configuration

        oc env dc/web-ui SSO_URL=sso-${PRJ_CI}.${DOMAIN} SSO_CLIENT_ID=web-ui SSO_REALM=coolstore -n ${PRJ_COOLSTORE_PROD}

1. Wait for the web-ui to redeploy and after that SSO should be enabled for the application


