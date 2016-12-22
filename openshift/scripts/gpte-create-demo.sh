#!/bin/bash
###############################################################
# Prvisioning script to deploy the demo on GPTE environment   #
#
# Usage:
# gpte-deploy.sh [user-id] [guid] [openshift-master-url]
#
# Example:
# gpte-deploy.sh ssadeghi-redhat.com b2fs5 http://console.openshift.yourdomain.com
#
###############################################################

[ "$#" -lt 1 ] && echo "ERROR: Too few arguments. Check the docs" && exit 0

###############################################################
# CONFIGURATION                                               #
###############################################################
USER_ID=$1  # ssadeghi-redhat.com
GUID=$2 # unique identifier
OPENSHIFT_MASTER=$3
PROJECT_SUFFIX=$(echo $USER_ID | sed 's/-redhat.com//g')
#GITHUB_ACCOUNT=jbossdemocentral
GITHUB_ACCOUNT=siamaksade
GITHUB_REF=demo-1

###############################################################
# HELPER FUNCTIONS                                            #
###############################################################
# add_nexus_repo [repo-id] [repo-url]
function add_nexus_repo() {
  local _REPO_ID=$1
  local _REPO_URL=$2
  local _NEXUS_URL=http://nexus-cicd-$PROJECT_SUFFIX.$DOMAIN

  # Add Repo
  read -r -d '' REPO_JSON << EOM
{
   "data": {
      "repoType": "proxy",
      "id": "$_REPO_ID",
      "name": "$_REPO_ID",
      "browseable": true,
      "indexable": true,
      "notFoundCacheTTL": 1440,
      "artifactMaxAge": -1,
      "metadataMaxAge": 1440,
      "itemMaxAge": 1440,
      "repoPolicy": "RELEASE",
      "provider": "maven2",
      "providerRole": "org.sonatype.nexus.proxy.repository.Repository",
      "downloadRemoteIndexes": true,
      "autoBlockActive": true,
      "fileTypeValidation": true,
      "exposed": true,
      "checksumPolicy": "WARN",
      "remoteStorage": {
         "remoteStorageUrl": "$_REPO_URL",
         "authentication": null,
         "connectionSettings": null
      }
   }
}
EOM

  curl -s -v -f -X POST -H "Accept: application/json" -H "Content-Type: application/json" -d "$REPO_JSON" -u "admin:admin123" "$_NEXUS_URL/service/local/repositories"

  # Add to Public Repo Group
  GROUP_JSON=$(curl -s -H "Accept: application/json" -H "Content-Type: application/json" -f -X GET -u "admin:admin123" "$_NEXUS_URL/service/local/repo_groups/public")
  GROUP_JSON_WITH_REPO=$(echo $GROUP_JSON | sed "s/\"repositories\":\[/\"repositories\":[{\"id\": \"$_REPO_ID\"},/g")
  curl -s -f -X PUT  -v -H "Accept: application/json" -H "Content-Type: application/json" -d "$GROUP_JSON_WITH_REPO" -u "admin:admin123" "$_NEXUS_URL/service/local/repo_groups/public"
}

###############################################################
# DEPLOY FUNCTIONS                                            #
###############################################################

# Create Projects
function create_projects() {
  oc new-project coolstore-test-$PROJECT_SUFFIX --display-name='Coolstore TEST' --description='Coolstore Test Environment'
  oc new-project coolstore-stage-$PROJECT_SUFFIX --display-name='Coolstore STAGE' --description='Coolstore Staging Environment'
  oc new-project coolstore-prod-$PROJECT_SUFFIX --display-name='Coolstore PROD' --description='Coolstore Production Environment'
  oc new-project inventory-dev-$PROJECT_SUFFIX --display-name='Inventory DEV' --description='Inventory Dev Environment'
  oc new-project cicd-$PROJECT_SUFFIX --display-name='CI/CD Infra' --description='CI/CD Infra Environment'
}

# Extract domain and set DOMAIN variable
function extract_and_set_domain() {
  oc create route edge testroute --service=testsvc --port=80 -n cicd-$PROJECT_SUFFIX >/dev/null
  DOMAIN=$(oc get route testroute -o template --template='{{.spec.host}}' -n cicd-$PROJECT_SUFFIX | sed "s/testroute-cicd-$PROJECT_SUFFIX.//g")
  oc delete route testroute >/dev/null
}

# Deploy Gogs
function deploy_gogs() {
  #oc process -f https://raw.githubusercontent.com/OpenShiftDemos/gogs-openshift-docker/master/openshift/gogs-persistent-template.yaml -v HOSTNAME=X | oc create -f -
  oc process -f https://raw.githubusercontent.com/siamaksade/gogs-openshift-docker/master/openshift/gogs-persistent-template.yaml -v HOSTNAME=gogs-cicd-$PROJECT_SUFFIX.$DOMAIN | oc create -f - -n cicd-$PROJECT_SUFFIX
}

# Deploy Nexus
function deploy_nexus() {
  oc new-app sonatype/nexus:2.14.2 -n cicd-$PROJECT_SUFFIX -e CONTEXT_PATH=/
  oc expose svc/nexus -n cicd-$PROJECT_SUFFIX
  oc set probe dc/nexus --liveness --failure-threshold 3 --initial-delay-seconds 30 -- echo ok -n cicd-$PROJECT_SUFFIX
  oc set probe dc/nexus --readiness --failure-threshold 3 --initial-delay-seconds 30 --get-url=http://:8081/content/groups/public -n cicd-$PROJECT_SUFFIX
  oc volumes dc/nexus --add --name 'nexus-volume-1' --type 'pvc' --mount-path '/sonatype-work/' --claim-name 'nexus-pv' --claim-size '2G' --overwrite -n cicd-$PROJECT_SUFFIX

  # Add Nexus repos
  echo "Waiting for Nexus to start..."
  local _NEXUS_URL=http://nexus-cicd-$PROJECT_SUFFIX.$DOMAIN
  local _x=1
  curl -s $_NEXUS_URL >/dev/null
  while [ ! $? -eq 0 ] # wait for Nexus to be ready
  do
    sleep 10
    echo "."
    x=$(( $_x + 1 ))
    if [ $_x -gt 100 ]
    then
      exit 255
    fi
    curl -s $_NEXUS_URL >/dev/null
  done

  add_nexus_repo jboss-ga https://maven.repository.redhat.com/ga/
  add_nexus_repo jboss-ea https://maven.repository.redhat.com/earlyaccess/all/
  add_nexus_repo jboss-ce https://repository.jboss.org/nexus/content/groups/public/
  add_nexus_repo jboss-techpreview https://maven.repository.redhat.com/techpreview/all
}

# Deploy Jenkins
function deploy_jenkins() {
  oc new-app jenkins-persistent -l app=jenkins -p JENKINS_PASSWORD=openshift -n cicd-$PROJECT_SUFFIX
}

# Deploy Coolstore into Coolstore TEST project
function deploy_coolstore_test_env() {
  oc process \
    -f https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/coolstore-template.yaml \
    -v GIT_REF=$GITHUB_REF \
    -v MAVEN_MIRROR_URL=http://nexus-cicd-$PROJECT_SUFFIX/content/groups/public \
    | oc create -f - -n coolstore-test-$PROJECT_SUFFIX
}

# Deploy Inventory Service into Inventory DEV project
function deploy_inventory_service() {
  oc process \
    -f https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/services/inventory-service.json \
    -v GIT_REF=$GITHUB_REF \
    -v MAVEN_MIRROR_URL=http://nexus-cicd-$PROJECT_SUFFIX:8081/content/groups/public \
    | oc create -f - -n inventory-dev-$PROJECT_SUFFIX
}

# Must be in the end not interfere with user quota during provisioning
function set_user_as_admin() {
  oc adm policy add-role-to-user admin $UID -n coolstore-test-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin $UID -n coolstore-stage-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin $UID -n coolstore-prod-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin $UID -n inventory-dev-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin $UID -n cicd-$PROJECT_SUFFIX
}


###############################################################
# DEPLOY DEMO                                                 #
###############################################################
create_projects
extract_and_set_domain
deploy_gogs
deploy_nexus
deploy_jenkins
deploy_coolstore_test_env
deploy_inventory_service
oc project default # GPTE convention
set_user_as_admin
