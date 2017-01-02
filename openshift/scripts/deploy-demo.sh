#!/bin/bash
###############################################################
# Prvisioning script to deploy the demo on an OpenShift environment
#
# Usage:
# gpte-deploy.sh [user-id] [guid] [openshift-master-url]
#
# Example:
# deploy-demo.sh ssadeghi-redhat.com b2fs5 http://console.openshift.yourdomain.com
#
###############################################################

###############################################################
# CONFIGURATION                                               #
###############################################################
USER_ID=${1:-demo}  # e.g. ssadeghi-redhat.com
GUID=$2 # unique identifier
OPENSHIFT_MASTER=$3
PROJECT_SUFFIX=$(echo $USER_ID | sed 's/-redhat.com//g')
#GITHUB_ACCOUNT=jbossdemocentral
GITHUB_ACCOUNT=siamaksade
GITHUB_REF=demo-1

###############################################################
# DEPLOY FUNCTIONS                                            #
###############################################################

# Create Infra Project
function create_infra_project() {
  echo_header "Creating infra project..."
  oc new-project cicd-$PROJECT_SUFFIX --display-name='CI/CD Infra' --description='CI/CD Infra Environment'
}

# Create Application Project
function create_app_projects() {
  echo_header "Creating application projects..."
  oc new-project coolstore-test-$PROJECT_SUFFIX --display-name='Coolstore TEST' --description='Coolstore Test Environment'
  oc new-project coolstore-stage-$PROJECT_SUFFIX --display-name='Coolstore STAGE' --description='Coolstore Staging Environment'
  oc new-project coolstore-prod-$PROJECT_SUFFIX --display-name='Coolstore PROD' --description='Coolstore Production Environment'
  oc new-project inventory-dev-$PROJECT_SUFFIX --display-name='Inventory DEV' --description='Inventory Dev Environment'
}

# Extract domain and set DOMAIN variable
function extract_and_set_domain() {
  oc create route edge testroute --service=testsvc --port=80 -n cicd-$PROJECT_SUFFIX >/dev/null
  DOMAIN=$(oc get route testroute -o template --template='{{.spec.host}}' -n cicd-$PROJECT_SUFFIX | sed "s/testroute-cicd-$PROJECT_SUFFIX.//g")
  oc delete route testroute -n cicd-$PROJECT_SUFFIX >/dev/null
}

# Deploy Gogs
function deploy_gogs() {
  echo_header "Deploying Gogs git server..."
  oc process -f https://raw.githubusercontent.com/OpenShiftDemos/gogs-openshift-docker/master/openshift/gogs-persistent-template.yaml -v HOSTNAME=gogs-cicd-$PROJECT_SUFFIX.$DOMAIN | oc create -f - -n cicd-$PROJECT_SUFFIX
}

# Deploy Nexus
function deploy_nexus() {
  echo_header "Deploying Sonatype Nexus repository manager..."
  oc process -f https://raw.githubusercontent.com/OpenShiftDemos/nexus/master/nexus2-persistent-template.yaml | oc create -f - -n cicd-$PROJECT_SUFFIX
}

# Wait till Nexus is ready
function wait_for_nexus_to_be_ready() {
  echo_header "Waiting for Nexus to be ready..."
  x=1
  while [ -z "$(oc get ep nexus -o yaml -n cicd-$PROJECT_SUFFIX | grep '\- addresses:')" ]
  do
    echo "."
    sleep 5
    x=$(( $x + 1 ))
    if [ $x -gt 120 ]
    then
      echo "Nexus still not ready, I GIVE UP!"
      exit 255
    fi
  done
  echo "Nexus is ready!"
}

# Deploy Jenkins
function deploy_jenkins() {
  echo_header "Deploying Jenkins..."
  oc new-app jenkins-persistent -l app=jenkins -p JENKINS_PASSWORD=openshift -n cicd-$PROJECT_SUFFIX
}

# Deploy Coolstore into Coolstore TEST project
function deploy_coolstore_test_env() {
  echo_header "Deploying CoolStore app into coolstore-test-$PROJECT_SUFFIX project..."
  oc process -f https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/coolstore-template.yaml -v GIT_REF=$GITHUB_REF -v MAVEN_MIRROR_URL=http://nexus.cicd-$PROJECT_SUFFIX.svc.cluster.local:8081/content/groups/public | oc create -f - -n coolstore-test-$PROJECT_SUFFIX
}

# Deploy Inventory Service into Inventory DEV project
function deploy_inventory_service() {
  echo_header "Deploying Inventory service into inventory-dev-$PROJECT_SUFFIX project..."
  oc process -f https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/services/inventory-service.json -v GIT_REF=$GITHUB_REF -v MAVEN_MIRROR_URL=http://nexus.cicd-$PROJECT_SUFFIX.svc.cluster.local:8081/content/groups/public | oc create -f - -n inventory-dev-$PROJECT_SUFFIX
}

function set_user_as_admin() {
  oc adm policy add-role-to-user admin $USER_ID -n coolstore-test-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin $USER_ID -n coolstore-stage-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin $USER_ID -n coolstore-prod-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin $USER_ID -n inventory-dev-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin $USER_ID -n cicd-$PROJECT_SUFFIX
}

# GPTE convention
function set_default_project() {
  if [ "$(oc whoami)" == 'system:admin' ]
  then
    oc project default
  fi
}

function echo_header() {
  echo "###############################################"
  echo $1
  echo "###############################################"
}

###############################################################
# DEPLOY DEMO                                                 #
###############################################################
create_infra_project
extract_and_set_domain
deploy_nexus
deploy_gogs
deploy_jenkins

create_app_projects
wait_for_nexus_to_be_ready
deploy_coolstore_test_env
deploy_inventory_service

set_default_project
set_user_as_admin # must be the last not to interfere with user quota during provisioning
