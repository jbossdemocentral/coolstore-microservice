#!/bin/bash
################################################################################
# Prvisioning script to deploy the demo on an OpenShift environment            #
################################################################################
function usage() {
    echo
    echo "Usage:"
    echo " $0 [command] [options]"
    echo " $0 --help"
    echo
    echo "Example:"
    echo " $0 --deploy --maven-mirror-url http://nexus.repo.com/content/groups/public/ --project-suffix s40d"
    echo
    echo "Commands:"
    echo "   --deploy            Set up the demo projects and deploy demo apps"
    echo "   --delete            Clean up and remove demo projects and objects"
    echo "   --verify            Verify the demo is deployed correctly"
    echo "   --idle              Make all demo servies idle"
    echo 
    echo "Options:"
    echo "   --user              The admin user for the demo projects. mandatory if logged in as system:admin"
    echo "   --maven-mirror-url  Use the given Maven repository for builds. If not specifid, a Nexus container is deployed in the demo"
    echo "   --project-suffix    Suffix to be added to demo project names e.g. ci-SUFFIX. If empty, user will be used as suffix"
    echo "   --minimal           Scale all pods except the absolute essential ones to zero to lower memory and cpu footprint"
    echo "   --ephemeral         Deploy demo without persistent storage"
    echo "   --run-verify        Run verify after provisioning"
}

ARG_USERNAME=
ARG_PROJECT_SUFFIX=
ARG_MAVEN_MIRROR_URL=
ARG_MINIMAL=false
ARG_EPHEMERAL=false
ARG_COMMAND=deploy
ARG_RUN_VERIFY=false

while :; do
    case $1 in
        --deploy)
            ARG_COMMAND=deploy
            ;;
        --delete)
            ARG_COMMAND=delete
            ;;
        --verify)
            ARG_COMMAND=verify
            ;;
        --idle)
            ARG_COMMAND=idle
            ;;
        --user)
            if [ -n "$2" ]; then
                ARG_USERNAME=$2
                shift
            else
                printf 'ERROR: "--user" requires a non-empty value.\n' >&2
                exit 1
            fi
            ;;
        --maven-mirror-url)
            if [ -n "$2" ]; then
                ARG_MAVEN_MIRROR_URL=$2
                shift
            else
                printf 'ERROR: "--maven-mirror-url" requires a non-empty value.\n' >&2
                exit 1
            fi
            ;;
        --project-suffix)
            if [ -n "$2" ]; then
                ARG_PROJECT_SUFFIX=$2
                shift
            else
                printf 'ERROR: "--project-suffix" requires a non-empty value.\n' >&2
                exit 1
            fi
            ;;
        --minimal)
            ARG_MINIMAL=true
            ;;
        --ephemeral)
            ARG_EPHEMERAL=true
            ;;
        --run-verify)
            ARG_RUN_VERIFY=true
            ;;
        -h|--help)
            usage
            exit
            ;;
        --)
            shift
            break
            ;;
        -?*)
            printf 'WARN: Unknown option (ignored): %s\n' "$1" >&2
            shift
            ;;
        *)               # Default case: If no more options then break out of the loop.
            break
    esac

    shift
done

################################################################################
# CONFIGURATION                                                                #
################################################################################
LOGGEDIN_USER=$(oc whoami)
OPENSHIFT_USER=${ARG_USERNAME:-$LOGGEDIN_USER}

# project
PRJ_SUFFIX=${ARG_PROJECT_SUFFIX:-`echo $OPENSHIFT_USER | sed -e 's/[-@].*//g'`}
PRJ_CI=ci-$PRJ_SUFFIX
PRJ_COOLSTORE_TEST=coolstore-test-$PRJ_SUFFIX
PRJ_COOLSTORE_PROD=coolstore-prod-$PRJ_SUFFIX
PRJ_INVENTORY=inventory-dev-$PRJ_SUFFIX
PRJ_DEVELOPER=developer-$PRJ_SUFFIX

# config
GITHUB_ACCOUNT=${GITHUB_ACCOUNT:-jbossdemocentral}
GITHUB_REF=${GITHUB_REF:-master}
GITHUB_URI=https://github.com/$GITHUB_ACCOUNT/coolstore-microservice.git

# maven 
MAVEN_MIRROR_URL=${ARG_MAVEN_MIRROR_URL:-http://nexus.$PRJ_CI.svc.cluster.local:8081/content/groups/public}

GOGS_USER=developer
GOGS_PASSWORD=developer
GOGS_ADMIN_USER=team
GOGS_ADMIN_PASSWORD=team

WEBHOOK_SECRET=UfW7gQ6Jx4

################################################################################
# FUNCTIONS                                                                    #
################################################################################

function print_info() {
  echo_header "Configuration"

  OPENSHIFT_MASTER=$(oc status | head -1 | sed 's#.*\(https://[^ ]*\)#\1#g') # must run after projects are created

  echo "OpenShift master:    $OPENSHIFT_MASTER"
  echo "Current user:        $LOGGEDIN_USER"
  echo "Minimal setup:       $ARG_MINIMAL"
  echo "Ephemeral:           $ARG_EPHEMERAL"
  echo "Project suffix:      $PRJ_SUFFIX"
  echo "GitHub repo:         https://github.com/$GITHUB_ACCOUNT/coolstore-microservice"
  echo "GitHub branch/tag:   $GITHUB_REF"
  echo "Gogs url:            http://$GOGS_ROUTE"
  echo "Gogs admin user:     $GOGS_ADMIN_USER"
  echo "Gogs admin pwd:      $GOGS_ADMIN_PASSWORD"
  echo "Gogs user:           $GOGS_USER"
  echo "Gogs pwd:            $GOGS_PASSWORD"
  echo "Gogs webhook secret: $WEBHOOK_SECRET"
  echo "Maven mirror url:    $MAVEN_MIRROR_URL"
}

# waits while the condition is true until it becomes false or it times out
function wait_while_empty() {
  local _NAME=$1
  local _TIMEOUT=$(($2/5))
  local _CONDITION=$3

  echo "Waiting for $_NAME to be ready..."
  local x=1
  while [ -z "$(eval ${_CONDITION})" ]
  do
    echo "."
    sleep 5
    x=$(( $x + 1 ))
    if [ $x -gt $_TIMEOUT ]
    then
      echo "$_NAME still not ready, I GIVE UP!"
      exit 255
    fi
  done

  echo "$_NAME is ready."
}

function remove_storage_claim() {
  local _DC=$1
  local _VOLUME_NAME=$2
  local _CLAIM_NAME=$3
  local _PROJECT=$4
  oc volumes dc/$_DC --name=$_VOLUME_NAME --add -t emptyDir --overwrite -n $_PROJECT
  oc delete pvc $_CLAIM_NAME -n $_PROJECT
}

function delete_projects() {
  oc delete project $PRJ_COOLSTORE_TEST $PRJ_DEVELOPER $PRJ_COOLSTORE_PROD $PRJ_INVENTORY $PRJ_CI
}

# Create Infra Project
function create_projects() {
  echo_header "Creating project..."

  echo "Creating project $PRJ_CI"
  oc new-project $PRJ_CI --display-name='CI/CD' --description='CI/CD Components (Jenkins, Gogs, etc)' >/dev/null
  echo "Creating project $PRJ_COOLSTORE_TEST"
  oc new-project $PRJ_COOLSTORE_TEST --display-name='CoolStore TEST' --description='CoolStore Test Environment' >/dev/null
  echo "Creating project $PRJ_COOLSTORE_PROD"
  oc new-project $PRJ_COOLSTORE_PROD --display-name='CoolStore PROD' --description='CoolStore Production Environment' >/dev/null
  echo "Creating project $PRJ_INVENTORY"
  oc new-project $PRJ_INVENTORY --display-name='Inventory TEST' --description='Inventory Test Environment' >/dev/null
  echo "Creating project $PRJ_DEVELOPER"
  oc new-project $PRJ_DEVELOPER --display-name='Developer Project' --description='Personal Developer Project' >/dev/null

  for project in $PRJ_CI $PRJ_COOLSTORE_TEST $PRJ_COOLSTORE_PROD $PRJ_INVENTORY $PRJ_DEVELOPER
  do
    oc adm policy add-role-to-group admin system:serviceaccounts:$PRJ_CI -n $project
    oc adm policy add-role-to-group admin system:serviceaccounts:$project -n $project
  done

  if [ $LOGGEDIN_USER == 'system:admin' ] ; then
    for project in $PRJ_CI $PRJ_COOLSTORE_TEST $PRJ_COOLSTORE_PROD $PRJ_INVENTORY $PRJ_DEVELOPER
    do
      oc adm policy add-role-to-user admin $ARG_USERNAME -n $project
      oc annotate --overwrite namespace $project demo=demo1-$PRJ_SUFFIX demo=demo-modern-arch-$PRJ_SUFFIX
    done
    oc adm pod-network join-projects --to=$PRJ_CI $PRJ_COOLSTORE_TEST $PRJ_DEVELOPER $PRJ_COOLSTORE_PROD $PRJ_INVENTORY >/dev/null 2>&1
  fi

  # Hack to extract domain name when it's not determine in
  # advanced e.g. <user>-<project>.4s23.cluster
  oc create route edge testroute --service=testsvc --port=80 -n $PRJ_CI >/dev/null
  DOMAIN=$(oc get route testroute -o template --template='{{.spec.host}}' -n $PRJ_CI | sed "s/testroute-$PRJ_CI.//g")
  GOGS_ROUTE="gogs-$PRJ_CI.$DOMAIN"
  oc delete route testroute -n $PRJ_CI >/dev/null
}

# Add Inventory Service Template
function add_inventory_template_to_projects() {
  echo_header "Adding inventory template to $PRJ_DEVELOPER project"
  local _TEMPLATE=https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/inventory-template.json
  curl -sL $_TEMPLATE | tr -d '\n' | tr -s '[:space:]' \
    | sed "s|\"MAVEN_MIRROR_URL\", \"value\": \"\"|\"MAVEN_MIRROR_URL\", \"value\": \"$MAVEN_MIRROR_URL\"|g" \
    | sed "s|\"https://github.com/jbossdemocentral/coolstore-microservice\"|\"http://$GOGS_ROUTE/$GOGS_USER/coolstore-microservice.git\"|g" \
    | oc create -f - -n $PRJ_DEVELOPER
}

# Deploy Nexus
function deploy_nexus() {
  if [ -z "$ARG_MAVEN_MIRROR_URL" ] ; then # no maven mirror specified
    local _TEMPLATE="https://raw.githubusercontent.com/OpenShiftDemos/nexus/master/nexus2-persistent-template.yaml"
    if [ "$ARG_EPHEMERAL" = true ] ; then
      _TEMPLATE="https://raw.githubusercontent.com/OpenShiftDemos/nexus/master/nexus2-template.yaml"
    fi

    echo_header "Deploying Sonatype Nexus repository manager..."
    echo "Using template $_TEMPLATE"
    oc process -f $_TEMPLATE -n $PRJ_CI | oc create -f - -n $PRJ_CI
    sleep 5
    oc set resources dc/nexus --limits=cpu=1,memory=2Gi --requests=cpu=200m,memory=1Gi -n $PRJ_CI
  else
    echo_header "Using existng Maven mirror: $ARG_MAVEN_MIRROR_URL"
  fi
}

# Wait till Nexus is ready
function wait_for_nexus_to_be_ready() {
  if [ "$ARG_MINIMAL" = true ] ; then
    return
  fi

  if [ -z "$ARG_MAVEN_MIRROR_URL" ] ; then # no maven mirror specified
    wait_while_empty "Nexus" 600 "oc get ep nexus -o yaml -n $PRJ_CI | grep '\- addresses:'"
  fi
}

# Deploy Gogs
function deploy_gogs() {
  echo_header "Deploying Gogs git server..."
  
  local _TEMPLATE="https://raw.githubusercontent.com/OpenShiftDemos/gogs-openshift-docker/master/openshift/gogs-persistent-template.yaml"
  if [ "$ARG_EPHEMERAL" = true ] ; then
    _TEMPLATE="https://raw.githubusercontent.com/OpenShiftDemos/gogs-openshift-docker/master/openshift/gogs-template.yaml"
  fi

  local _DB_USER=gogs
  local _DB_PASSWORD=gogs
  local _DB_NAME=gogs
  local _GITHUB_REPO="https://github.com/$GITHUB_ACCOUNT/coolstore-microservice.git"

  echo "Using template $_TEMPLATE"
  oc process -f $_TEMPLATE -v HOSTNAME=$GOGS_ROUTE -v GOGS_VERSION=0.9.113 -v DATABASE_USER=$_DB_USER -v DATABASE_PASSWORD=$_DB_PASSWORD -v DATABASE_NAME=$_DB_NAME -v SKIP_TLS_VERIFY=true -n $PRJ_CI | oc create -f - -n $PRJ_CI

  sleep 5

  # wait for Gogs to be ready
  wait_while_empty "Gogs PostgreSQL" 600 "oc get ep gogs-postgresql -o yaml -n $PRJ_CI | grep '\- addresses:'"
  wait_while_empty "Gogs" 600 "oc get ep gogs -o yaml -n $PRJ_CI | grep '\- addresses:'"

  sleep 20

  # add admin user
  _RETURN=$(curl -o /dev/null -sL --post302 -w "%{http_code}" http://$GOGS_ROUTE/user/sign_up \
    --form user_name=$GOGS_ADMIN_USER \
    --form password=$GOGS_ADMIN_PASSWORD \
    --form retype=$GOGS_ADMIN_PASSWORD \
    --form email=$GOGS_ADMIN_USER@gogs.com)
  sleep 5

  # import GitHub repo
  read -r -d '' _DATA_JSON << EOM
{
  "clone_addr": "$_GITHUB_REPO",
  "uid": 1,
  "repo_name": "coolstore-microservice"
}
EOM

  _RETURN=$(curl -o /dev/null -sL -w "%{http_code}" -H "Content-Type: application/json" -d "$_DATA_JSON" -u $GOGS_ADMIN_USER:$GOGS_ADMIN_PASSWORD -X POST http://$GOGS_ROUTE/api/v1/repos/migrate)
  if [ $_RETURN != "201" ] && [ $_RETURN != "200" ] ; then
    echo "WARNING: Failed (http code $_RETURN) to import GitHub repo $_REPO to Gogs"
  else
    echo "CoolStore GitHub repo imported to Gogs"
  fi

  # create user
  read -r -d '' _DATA_JSON << EOM
{
    "login_name": "$GOGS_USER",
    "username": "$GOGS_USER",
    "email": "$GOGS_USER@gogs.com",
    "password": "$GOGS_PASSWORD"
}
EOM
  _RETURN=$(curl -o /dev/null -sL -w "%{http_code}" -H "Content-Type: application/json" -d "$_DATA_JSON" -u $GOGS_ADMIN_USER:$GOGS_ADMIN_PASSWORD -X POST http://$GOGS_ROUTE/api/v1/admin/users)
  if [ $_RETURN != "201" ] && [ $_RETURN != "200" ] ; then
    echo "WARNING: Failed (http code $_RETURN) to create user $GOGS_USER"
  else
    echo "Gogs user created: $GOGS_USER"
  fi

  sleep 2

  # import tag to master
  local _CLONE_DIR=/tmp/$(date +%s)-coolstore-microservice
  rm -rf $_CLONE_DIR && \
      git clone http://$GOGS_ROUTE/$GOGS_ADMIN_USER/coolstore-microservice.git $_CLONE_DIR && \
      cd $_CLONE_DIR && \
      git branch -m master master-old && \
      git checkout $GITHUB_REF && \
      git branch -m $GITHUB_REF master && \
      git push -f http://$GOGS_ADMIN_USER:$GOGS_ADMIN_PASSWORD@$GOGS_ROUTE/$GOGS_ADMIN_USER/coolstore-microservice.git master && \
      rm -rf $_CLONE_DIR
}

# Deploy Jenkins
function deploy_jenkins() {
  echo_header "Deploying Jenkins..."
  
  if [ "$ARG_EPHEMERAL" = true ] ; then
    oc new-app jenkins-ephemeral -l app=jenkins -p MEMORY_LIMIT=1Gi -n $PRJ_CI
  else
    oc new-app jenkins-persistent -l app=jenkins -p MEMORY_LIMIT=1Gi -n $PRJ_CI
  fi

  sleep 2
  oc set resources dc/jenkins --limits=cpu=1,memory=2Gi --requests=cpu=200m,memory=1Gi -n $PRJ_CI
}

function remove_coolstore_storage_if_ephemeral() {
  local _PROJECT=$1
  if [ "$ARG_EPHEMERAL" = true ] ; then
    remove_storage_claim inventory-postgresql inventory-postgresql-data inventory-postgresql-pv $_PROJECT
    remove_storage_claim catalog-mongodb mongodb-data mongodb-data-pv $_PROJECT
  fi
}

function scale_down_deployments() {
  local _PROJECT=$1
	shift
	while test ${#} -gt 0
	do
	  oc scale --replicas=0 dc $1 -n $_PROJECT
	  shift
	done
}

# Deploy Coolstore into Coolstore TEST project
function deploy_coolstore_test_env() {
  local _TEMPLATE="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/coolstore-deployments-template.yaml"

  echo_header "Deploying CoolStore app into $PRJ_COOLSTORE_TEST project..."
  echo "Using deployment template $_TEMPLATE_DEPLOYMENT"
  oc process -f $_TEMPLATE -v APP_VERSION=test -v HOSTNAME_SUFFIX=$PRJ_COOLSTORE_TEST.$DOMAIN -n $PRJ_COOLSTORE_TEST | oc create -f - -n $PRJ_COOLSTORE_TEST
  sleep 2
  remove_coolstore_storage_if_ephemeral $PRJ_COOLSTORE_TEST

  # scale down to zero if minimal
  if [ "$ARG_MINIMAL" == true ] ; then
    scale_down_deployments $PRJ_COOLSTORE_TEST coolstore-gw web-ui inventory cart catalog catalog-mongodb inventory-postgresql pricing
  fi  
}

# Deploy Coolstore into Coolstore PROD project
function deploy_coolstore_prod_env() {
  local _TEMPLATE_DEPLOYMENT="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/coolstore-deployments-template.yaml"
  local _TEMPLATE_BLUEGREEN="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/inventory-bluegreen-template.yaml"
  local _TEMPLATE_NETFLIX="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/netflix-oss-list.yaml"

  echo_header "Deploying CoolStore app into $PRJ_COOLSTORE_PROD project..."
  echo "Using deployment template $_TEMPLATE_DEPLOYMENT"
  echo "Using bluegreen template $_TEMPLATE_BLUEGREEN"
  echo "Using Netflix OSS template $_TEMPLATE_NETFLIX"

  oc process -f $_TEMPLATE_DEPLOYMENT -v APP_VERSION=prod -v HOSTNAME_SUFFIX=$PRJ_COOLSTORE_PROD.$DOMAIN -n $PRJ_COOLSTORE_PROD | oc create -f - -n $PRJ_COOLSTORE_PROD
  sleep 2
  oc delete all,pvc -l application=inventory --now --ignore-not-found -n $PRJ_COOLSTORE_PROD
  sleep 2
  oc process -f $_TEMPLATE_BLUEGREEN -v APP_VERSION_BLUE=prod-blue -v APP_VERSION_GREEN=prod-green -v HOSTNAME_SUFFIX=$PRJ_COOLSTORE_PROD.$DOMAIN -n $PRJ_COOLSTORE_PROD | oc create -f - -n $PRJ_COOLSTORE_PROD
  sleep 2
  oc create -f $_TEMPLATE_NETFLIX -n $PRJ_COOLSTORE_PROD
  sleep 2
  remove_coolstore_storage_if_ephemeral $PRJ_COOLSTORE_PROD

  # scale down most pods to zero if minimal
  if [ "$ARG_MINIMAL" = true ] ; then
    scale_down_deployments $PRJ_COOLSTORE_PROD cart turbine-server hystrix-dashboard pricing
  fi  
}

# Deploy Inventory service into Inventory DEV project
function deploy_inventory_dev_env() {
  local _TEMPLATE="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/inventory-template.json"

  echo_header "Deploying Inventory service into $PRJ_INVENTORY project..."
  echo "Using template $_TEMPLATE"
  oc process -f $_TEMPLATE -v GIT_URI=http://$GOGS_ROUTE/$GOGS_ADMIN_USER/coolstore-microservice.git -v MAVEN_MIRROR_URL=$MAVEN_MIRROR_URL -n $PRJ_INVENTORY | oc create -f - -n $PRJ_INVENTORY
  sleep 2
  # scale down to zero if minimal
  if [ "$ARG_MINIMAL" = true ] ; then
    scale_down_deployments $PRJ_INVENTORY inventory inventory-postgresql
  fi  
}

function build_images() {
  local _TEMPLATE_BUILDS="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/coolstore-builds-template.yaml"
  echo "Using build template $_TEMPLATE_BUILDS"
  oc process -f $_TEMPLATE_BUILDS -v GIT_URI=$GITHUB_URI -v GIT_REF=$GITHUB_REF -v MAVEN_MIRROR_URL=$MAVEN_MIRROR_URL -n $PRJ_COOLSTORE_TEST | oc create -f - -n $PRJ_COOLSTORE_TEST

  sleep 10

  # build images
  for buildconfig in web-ui inventory cart catalog coolstore-gw pricing
  do
    oc start-build $buildconfig -n $PRJ_COOLSTORE_TEST
    wait_while_empty "$buildconfig build" 180 "oc get builds -n $PRJ_COOLSTORE_TEST | grep $buildconfig | grep Running"
    sleep 10
  done
}

function promote_images() {
  # wait for builds
  for buildconfig in coolstore-gw web-ui inventory cart catalog pricing
  do
    wait_while_empty "$buildconfig image" 600 "oc get builds -n $PRJ_COOLSTORE_TEST | grep $buildconfig | grep -v Running"
    sleep 10
  done

  # verify successful builds
  for buildconfig in coolstore-gw web-ui inventory cart catalog pricing
  do
    if [ -z "$(oc get builds -n $PRJ_COOLSTORE_TEST | grep $buildconfig | grep Complete)" ]; then
      echo "ERROR: Build $buildconfig did not complete successfully"
      exit 255
    fi
  done

  # remove buildconfigs. Jenkins does that!
  oc delete bc --all -n $PRJ_COOLSTORE_TEST

  for is in coolstore-gw web-ui cart catalog pricing
  do
    oc tag $PRJ_COOLSTORE_TEST/$is:latest $PRJ_COOLSTORE_TEST/$is:test
    oc tag $PRJ_COOLSTORE_TEST/$is:latest $PRJ_COOLSTORE_PROD/$is:prod
    oc tag $PRJ_COOLSTORE_TEST/$is:latest -d
  done

  oc tag $PRJ_COOLSTORE_TEST/inventory:latest $PRJ_INVENTORY/inventory:latest
  oc tag $PRJ_COOLSTORE_TEST/inventory:latest $PRJ_COOLSTORE_TEST/inventory:test
  oc tag $PRJ_COOLSTORE_TEST/inventory:latest $PRJ_COOLSTORE_PROD/inventory:prod-green
  oc tag $PRJ_COOLSTORE_TEST/inventory:latest $PRJ_COOLSTORE_PROD/inventory:prod-blue
  oc tag $PRJ_COOLSTORE_TEST/inventory:latest -d

  # remove fis image
  oc delete is fis-java-openshift -n $PRJ_COOLSTORE_TEST --ignore-not-found
}

function deploy_pipeline() {
  echo_header "Configuring CI/CD..."

  local _PIPELINE_NAME=inventory-pipeline
  local _TEMPLATE=https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/inventory-pipeline-template.yaml

  oc process -f $_TEMPLATE -v PIPELINE_NAME=$_PIPELINE_NAME -v DEV_PROJECT=$PRJ_INVENTORY -v TEST_PROJECT=$PRJ_COOLSTORE_TEST -v PROD_PROJECT=$PRJ_COOLSTORE_PROD -v GENERIC_WEBHOOK_SECRET=$WEBHOOK_SECRET -n $PRJ_CI | oc create -f - -n $PRJ_CI

  # configure webhook to trigger pipeline
  read -r -d '' _DATA_JSON << EOM
{
  "type": "gogs",
  "config": {
    "url": "https://$OPENSHIFT_MASTER/oapi/v1/namespaces/$PRJ_CI/buildconfigs/$_PIPELINE_NAME/webhooks/$WEBHOOK_SECRET/generic",
    "content_type": "json"
  },
  "events": [
    "push"
  ],
  "active": true
}
EOM


  _RETURN=$(curl -o /dev/null -sL -w "%{http_code}" -H "Content-Type: application/json" -d "$_DATA_JSON" -u $GOGS_ADMIN_USER:$GOGS_ADMIN_PASSWORD -X POST http://$GOGS_ROUTE/api/v1/repos/$GOGS_ADMIN_USER/coolstore-microservice/hooks)
  if [ $_RETURN != "201" ] && [ $_RETURN != "200" ] ; then
   echo "WARNING: Failed (http code $_RETURN) to configure webhook on Gogs"
  fi
}

function verify_build_and_deployments() {
  echo_header "Verifying build and deployments"
  # verify builds
  local _BUILDS_FAILED=false
  for buildconfig in coolstore-gw web-ui inventory cart catalog 
  do
    if [ -n "$(oc get builds -n $PRJ_COOLSTORE_TEST | grep $buildconfig | grep Failed)" ] && [ -z "$(oc get builds -n $PRJ_COOLSTORE_TEST | grep $buildconfig | grep Complete)" ]; then
      _BUILDS_FAILED=true
      echo "WARNING: Build $project/$buildconfig has failed. Starging a new build..."
      oc start-build $buildconfig -n $PRJ_COOLSTORE_TEST --wait
    fi
  done

  # promote images if builds had failed
  if [ "$_BUILDS_FAILED" = true ]; then
    promote_images
    deploy_pipeline
  fi

  # verify deployments
  for project in $PRJ_COOLSTORE_TEST $PRJ_COOLSTORE_PROD $PRJ_CI $PRJ_INVENTORY
  do
    local _DC=
    for dc in $(oc get dc -n $project -o=custom-columns=:.metadata.name,:.status.replicas); do
      if [ $dc = 0 ] && [ -z "$(oc get pods -n $project | grep "$dc-[0-9]\+-deploy" | grep Running)" ] ; then
        echo "WARNING: Deployment $project/$_DC in project $project is not complete. Starting a new deployment..."
        oc deploy $_DC --cancel -n $project >/dev/null
        sleep 5
        oc deploy $_DC --latest --follow -n $project
      fi
      _DC=$dc
    done
  done
}

function deploy_guides() {
  echo_header "Deploying Demo Guides"
  local _DEMO_CONTENT_URL="https://raw.githubusercontent.com/osevg/workshopper-content/stable"
  local _DEMOS="$_DEMO_CONTENT_URL/demos/_demo-all.yml,$_DEMO_CONTENT_URL/demos/_demo-msa.yml,$_DEMO_CONTENT_URL/demos/_demo-agile-integration.yml,$_DEMO_CONTENT_URL/demos/_demo-cicd-eap.yml"
  oc new-app --name=guides jboss-eap70-openshift~https://github.com/osevg/workshopper.git#stable -n $PRJ_CI -e WORKSHOPS_URLS=$_DEMOS -e CONTENT_URL_PREFIX=$_DEMO_CONTENT_URL -e PROJECT_SUFFIX=$PRJ_SUFFIX -e GOGS_URL=http://$GOGS_ROUTE -e GOGS_DEV_REPO_URL_PREFIX=http://$GOGS_ROUTE/$GOGS_USER/coolstore-microservice -e JENKINS_URL=http://jenkins-$PRJ_CI.$DOMAIN -e COOLSTORE_WEB_PROD_URL=http://web-ui-$PRJ_COOLSTORE_PROD.$DOMAIN -e HYSTRIX_PROD_URL=http://hystrix-dashboard-$PRJ_COOLSTORE_PROD.$DOMAIN -e GOGS_DEV_USER=$GOGS_USER -e GOGS_DEV_PASSWORD=$GOGS_PASSWORD -e GOGS_REVIEWER_USER=$GOGS_ADMIN_USER -e GOGS_REVIEWER_PASSWORD=$GOGS_ADMIN_PASSWORD -e OCP_VERSION=3.4 -n $PRJ_CI
  oc expose svc/guides -n $PRJ_CI
  oc cancel-build bc/guides -n $PRJ_CI
  oc set env bc/guides MAVEN_MIRROR_URL=$MAVEN_MIRROR_URL -n $PRJ_CI
  oc start-build guides -n $PRJ_CI
  oc set probe dc/guides -n $PRJ_CI --readiness -- /bin/bash -c /opt/eap/bin/readinessProbe.sh
  oc set probe dc/guides -n $PRJ_CI --liveness -- /bin/bash -c /opt/eap/bin/livenessProbe.sh
  oc set resources dc/guides --limits=cpu=500m,memory=1Gi --requests=cpu=100m,memory=512Mi -n $PRJ_CI
  oc set resources bc/guides --limits=cpu=1,memory=2Gi --requests=cpu=100m,memory=512Mi -n $PRJ_CI

  if [ "$ARG_MINIMAL" = true ] ; then
    scale_down_deployments $PRJ_CI guides
  fi  
}

function make_idle() {
  echo_header "Idling Services"
  oc idle -n $PRJ_CI --all
  oc idle -n $PRJ_COOLSTORE_TEST --all
  oc idle -n $PRJ_COOLSTORE_PROD --all
  oc idle -n $PRJ_INVENTORY --all
  oc idle -n $PRJ_DEVELOPER --all
}

# GPTE convention
function set_default_project() {
  if [ $LOGGEDIN_USER == 'system:admin' ] ; then
    oc project default >/dev/null
  fi
}

function echo_header() {
  echo
  echo "########################################################################"
  echo $1
  echo "########################################################################"
}

################################################################################
# MAIN: DEPLOY DEMO                                                            #
################################################################################

if [ "$LOGGEDIN_USER" == 'system:admin' ] && [ -z "$ARG_USERNAME" ] ; then
  # for verify and delete, --project-suffix is enough
  if [ "$ARG_COMMAND" == "delete" ] || [ "$ARG_COMMAND" == "verify" ] && [ -z "$ARG_PROJECT_SUFFIX" ]; then
    echo "--user or --project-suffix must be provided when running $ARG_COMMAND as 'system:admin'"
    exit 255
  # deploy command
  elif [ "$ARG_COMMAND" != "delete" ] && [ "$ARG_COMMAND" != "verify" ] ; then
    echo "--user must be provided when running $ARG_COMMAND as 'system:admin'"
    exit 255
  fi
fi

pushd ~
START=`date +%s`

echo_header "Multi-product MSA Demo ($(date))"

case "$ARG_COMMAND" in
    delete)
        echo "Delete MSA demo..."
        delete_projects
        exit 0
        ;;
      
    verify)
        echo "Verifying MSA demo..."
        print_info
        verify_build_and_deployments
        ;;

    idle)
        echo "Idling MSA demo..."
        print_info
        make_idle
        ;;

    *)
        echo "Deploying MSA demo..."
        create_projects 
        print_info
        deploy_nexus
        wait_for_nexus_to_be_ready
        build_images
        deploy_guides
        deploy_gogs
        deploy_jenkins
        add_inventory_template_to_projects
        deploy_coolstore_test_env
        deploy_coolstore_prod_env
        deploy_inventory_dev_env
        promote_images
        deploy_pipeline

        if [ "$ARG_RUN_VERIFY" = true ] ; then
          echo "Waiting for deployments to finish..."
          sleep 30
          verify_build_and_deployments
        fi
esac

set_default_project
popd

END=`date +%s`
echo
echo "Provisioning done! (Completed in $(( ($END - $START)/60 )) min $(( ($END - $START)%60 )) sec)"