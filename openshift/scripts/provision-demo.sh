#!/bin/bash
################################################################################
# Prvisioning script to deploy the demo on an OpenShift environment            #
################################################################################
function usage() {
    echo
    echo "Usage:"
    echo " $0 [--user <username>] [--maven-mirror-url <value> ] [--project-suffix <value>]"
    echo " $0 --help "
    echo
    echo "Example:"
    echo " $0 --maven-mirror-url http://nexus.repo.com/content/groups/public/ --project-suffix s40d"
    echo
    echo "If --user is not specified, current logged user will be the project admins"
    echo "If --maven-mirror-url is not specified, a Nexus container will be deployed and used"
    echo "If --project-suffix is not specified, if <username> specified it will be used as suffix. Default 'demo'"
}

ARG_USERNAME=
ARG_PROJECT_SUFFIX=
ARG_MAVEN_MIRROR_URL=
ARG_DELETE=false

while :; do
    case $1 in
        -h|--help)
            usage
            exit
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
        --delete)
            ARG_DELETE=true
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
OPENSHIFT_MASTER=$(oc whoami -c | sed 's#[^/]*/\([^/]*\)/[^/]*#\1#g')
LOGGEDIN_USER=$(oc whoami)
OPENSHIFT_USER=${ARG_USERNAME:-$LOGGEDIN_USER}

# project
PRJ_SUFFIX=${ARG_PROJECT_SUFFIX:-`echo $OPENSHIFT_USER | sed -e 's/[-@].*//g'`}
PRJ_LABEL=demo1-$PRJ_SUFFIX
PRJ_CI=ci-$PRJ_SUFFIX
PRJ_COOLSTORE_TEST=coolstore-test-$PRJ_SUFFIX
PRJ_COOLSTORE_PROD=coolstore-prod-$PRJ_SUFFIX
PRJ_INVENTORY=inventory-dev-$PRJ_SUFFIX
PRJ_DEVELOPER=developer-$PRJ_SUFFIX

# config
GITHUB_ACCOUNT=${GITHUB_ACCOUNT:-jbossdemocentral}
GITHUB_REF=${GITHUB_REF:-stable-ocp-3.4}
GITHUB_URI=https://github.com/$GITHUB_ACCOUNT/coolstore-microservice.git

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
  echo "OpenShift master:    $OPENSHIFT_MASTER"
  echo "Current user         $LOGGEDIN_USER"
  echo "Project suffix:      $PRJ_SUFFIX"
  echo "Project label:       $PRJ_LABEL"
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

function delete_projects() {
  oc delete project $PRJ_COOLSTORE_TEST $PRJ_DEVELOPER $PRJ_COOLSTORE_PROD $PRJ_INVENTORY $PRJ_CI
}

# Create Infra Project
function create_infra_project() {
  echo_header "Creating CI/CD infra project..."
  oc new-project $PRJ_CI --display-name='CI/CD' --description='CI/CD Components (Jenkins, Gogs, etc)'

  if [ $LOGGEDIN_USER == 'system:admin' ] ; then
    oc adm policy add-role-to-user admin $ARG_USERNAME -n $PRJ_CI
    oc annotate --overwrite namespace $PRJ_CI demo=$PRJ_LABEL
  fi

  # Hack to extract domain name when it's not determine in
  # advanced e.g. <user>-<project>.4s23.cluster
  oc create route edge testroute --service=testsvc --port=80 -n $PRJ_CI >/dev/null
  DOMAIN=$(oc get route testroute -o template --template='{{.spec.host}}' -n $PRJ_CI | sed "s/testroute-$PRJ_CI.//g")
  GOGS_ROUTE="gogs-$PRJ_CI.$DOMAIN"
  oc delete route testroute -n $PRJ_CI >/dev/null
}

# Create Application Project
function create_app_projects() {
  echo_header "Creating application projects..."
  oc new-project $PRJ_COOLSTORE_TEST --display-name='CoolStore TEST' --description='CoolStore Test Environment'
  oc new-project $PRJ_COOLSTORE_PROD --display-name='CoolStore PROD' --description='CoolStore Production Environment'
  oc new-project $PRJ_INVENTORY --display-name='Inventory TEST' --description='Inventory Test Environment'
  oc new-project $PRJ_DEVELOPER --display-name='Developer Project' --description='Personal Developer Project'

  if [ $LOGGEDIN_USER == 'system:admin' ] ; then
    for project in $PRJ_COOLSTORE_TEST $PRJ_COOLSTORE_PROD $PRJ_INVENTORY $PRJ_DEVELOPER
    do
      oc annotate --overwrite namespace $project demo=$PRJ_LABEL
    done
  fi

  # join project networks
  if [ $LOGGEDIN_USER == 'system:admin' ] ; then
    oc adm pod-network join-projects --to=$PRJ_CI $PRJ_COOLSTORE_TEST $PRJ_DEVELOPER $PRJ_COOLSTORE_PROD $PRJ_INVENTORY
  fi
}

# Add Inventory Service Template
function add_inventory_template_to_projects() {
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

    echo_header "Deploying Sonatype Nexus repository manager..."
    echo "Using template $_TEMPLATE"
    oc process -f $_TEMPLATE -n $PRJ_CI | oc create -f - -n $PRJ_CI
  else
    echo_header "Using existng Maven mirror: $ARG_MAVEN_MIRROR_URL"
  fi
}

# Wait till Nexus is ready
function wait_for_nexus_to_be_ready() {
  if [ -z "$ARG_MAVEN_MIRROR_URL" ] ; then # no maven mirror specified
    wait_while_empty "Nexus" 600 "oc get ep nexus -o yaml -n $PRJ_CI | grep '\- addresses:'"
  fi
}

# Deploy Gogs
function deploy_gogs() {
  echo_header "Deploying Gogs git server..."

  local _TEMPLATE="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/gogs-persistent-template.yaml"
  local _DB_USER=gogs
  local _DB_PASSWORD=gogs
  local _DB_NAME=gogs
  local _GITHUB_REPO="https://github.com/$GITHUB_ACCOUNT/coolstore-microservice.git"

  echo "Using template $_TEMPLATE"
  oc process -f $_TEMPLATE -v HOSTNAME=$GOGS_ROUTE -v GOGS_VERSION=0.9.113 -v DATABASE_USER=$_DB_USER -v DATABASE_PASSWORD=$_DB_PASSWORD -v DATABASE_NAME=$_DB_NAME -n $PRJ_CI | oc create -f - -n $PRJ_CI

  sleep 5

  # wait for Gogs to be ready
  wait_while_empty "Gogs PostgreSQL" 600 "oc get ep gogs-postgresql -o yaml -n $PRJ_CI | grep '\- addresses:'"
  wait_while_empty "Gogs" 600 "oc get ep gogs -o yaml -n $PRJ_CI | grep '\- addresses:'"

  # initialise Gogs
  _RETURN=$(curl -o /dev/null -sL --post302 -w "%{http_code}" http://$GOGS_ROUTE/install \
    --form db_type=PostgreSQL \
    --form db_host=gogs-postgresql:5432 \
    --form db_user=$_DB_USER \
    --form db_passwd=$_DB_PASSWORD \
    --form db_name=$_DB_NAME \
    --form ssl_mode=disable \
    --form db_path=data/gogs.db \
    --form "app_name=Gogs: Go Git Service" \
    --form repo_root_path=/opt/gogs/data/repositories \
    --form run_user=gogs \
    --form log_root_path=/opt/gogs/log \
    --form domain=localhost \
    --form ssh_port=22 \
    --form http_port=3000 \
    --form app_url=http://$GOGS_ROUTE/ \
    --form admin_name=$GOGS_ADMIN_USER \
    --form admin_passwd=$GOGS_ADMIN_PASSWORD \
    --form admin_confirm_passwd=$GOGS_ADMIN_PASSWORD \
    --form admin_email=$GOGS_ADMIN_USER@gogs.com)

  if [ $_RETURN != "200" ] ; then
    echo "WARNING: Failed (http code $_RETURN) to initialise Gogs"
    exit 255
  fi

  sleep 5

  # disable TLS verification for webhooks
  echo "Configuring and restarting Gogs"
  oc rsh $(oc get pod -o name -l deploymentconfig=gogs -n $PRJ_CI) /bin/bash -c "if ! grep TLS /opt/gogs/data/custom/conf/app.ini; then printf '[webhook]\nSKIP_TLS_VERIFY = true\n' >> /opt/gogs/data/custom/conf/app.ini ; fi" -n $PRJ_CI
  oc delete pod -l deploymentconfig=gogs -n $PRJ_CI >/dev/null
  wait_while_empty "Gogs" 300 "oc get ep gogs -o yaml -n $PRJ_CI | grep '\- addresses:'"

  sleep 30

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
  oc new-app jenkins-persistent -l app=jenkins -p MEMORY_LIMIT=1Gi -n $PRJ_CI
  oc set resources dc/jenkins --limits=cpu=1,memory=2Gi --requests=cpu=200m,memory=1Gi
}

# Deploy Coolstore into Coolstore TEST project
function deploy_coolstore_test_env() {
  local _TEMPLATE="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/coolstore-deployments-template.yaml"

  echo_header "Deploying CoolStore app into $PRJ_COOLSTORE_TEST project..."
  echo "Using deployment template $_TEMPLATE_DEPLOYMENT"
  oc process -f $_TEMPLATE -v APP_VERSION=test -v HOSTNAME_SUFFIX=$PRJ_COOLSTORE_TEST.$DOMAIN -n $PRJ_COOLSTORE_TEST | oc create -f - -n $PRJ_COOLSTORE_TEST
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
  oc delete all,pvc -l application=inventory --now -n $PRJ_COOLSTORE_PROD
  sleep 2
  oc process -f $_TEMPLATE_BLUEGREEN -v APP_VERSION_BLUE=prod-blue -v APP_VERSION_GREEN=prod-green -v HOSTNAME_SUFFIX=$PRJ_COOLSTORE_PROD.$DOMAIN -n $PRJ_COOLSTORE_PROD | oc create -f - -n $PRJ_COOLSTORE_PROD
  sleep 2
  oc create -f $_TEMPLATE_NETFLIX -n $PRJ_COOLSTORE_PROD
}

# Deploy Inventory service into Inventory DEV project
function deploy_inventory_dev_env() {
  local _TEMPLATE="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/inventory-template.json"

  echo_header "Deploying Inventory service into $PRJ_INVENTORY project..."
  echo "Using template $_TEMPLATE"
  oc process -f $_TEMPLATE -v GIT_URI=http://$GOGS_ROUTE/$GOGS_ADMIN_USER/coolstore-microservice.git -v MAVEN_MIRROR_URL=$MAVEN_MIRROR_URL -n $PRJ_INVENTORY | oc create -f - -n $PRJ_INVENTORY
}

# Prepare the BuildConfigs and Deployment for CI/CD
function build_and_tag_images_for_ci() {
  local _TEMPLATE_BUILDS="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/coolstore-builds-template.yaml"
  echo "Using build template $_TEMPLATE_BUILDS"
  oc process -f $_TEMPLATE_BUILDS -v GIT_URI=$GITHUB_URI -v GIT_REF=$GITHUB_REF -v MAVEN_MIRROR_URL=$MAVEN_MIRROR_URL -n $PRJ_COOLSTORE_TEST | oc create -f - -n $PRJ_COOLSTORE_TEST

  sleep 5

  # build images
  for buildconfig in coolstore-gw web-ui inventory cart catalog
  do
    oc start-build $buildconfig -n $PRJ_COOLSTORE_TEST
    wait_while_empty "$buildconfig build" 180 "oc get builds -n $PRJ_COOLSTORE_TEST | grep $buildconfig | grep Running"
    sleep 10
  done

  # wait for builds
  for buildconfig in coolstore-gw web-ui inventory cart catalog 
  do
    wait_while_empty "$buildconfig image" 600 "oc get builds -n $PRJ_COOLSTORE_TEST | grep $buildconfig | grep -v Running"
    sleep 10
  done

  # verify successful builds
  for buildconfig in coolstore-gw web-ui inventory cart catalog 
  do
    if [ -z "$(oc get builds -n $PRJ_COOLSTORE_TEST | grep $buildconfig | grep Complete)" ]; then
      echo "ERROR: Build $buildconfig did not complete successfully"
      exit 255
    fi
  done

  # remove buildconfigs. Jenkins does that!
  oc delete bc --all -n $PRJ_COOLSTORE_TEST

  for is in coolstore-gw web-ui cart catalog
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
  oc delete is fis-java-openshift -n $PRJ_COOLSTORE_TEST
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

function set_project_permissions() {
  if [ $LOGGEDIN_USER == "system:admin" ] ; then
    oc adm policy add-role-to-user admin $ARG_USERNAME -n $PRJ_COOLSTORE_TEST
    oc adm policy add-role-to-user admin $ARG_USERNAME -n $PRJ_DEVELOPER
    oc adm policy add-role-to-user admin $ARG_USERNAME -n $PRJ_COOLSTORE_PROD
    oc adm policy add-role-to-user admin $ARG_USERNAME -n $PRJ_INVENTORY
  fi

  oc adm policy add-role-to-group admin system:serviceaccounts:$PRJ_CI -n $PRJ_COOLSTORE_TEST
  oc adm policy add-role-to-group admin system:serviceaccounts:$PRJ_CI -n $PRJ_DEVELOPER
  oc adm policy add-role-to-group admin system:serviceaccounts:$PRJ_CI -n $PRJ_COOLSTORE_PROD
  oc adm policy add-role-to-group admin system:serviceaccounts:$PRJ_CI -n $PRJ_INVENTORY
}

function verify_deployments() {
  for project in $PRJ_COOLSTORE_TEST $PRJ_COOLSTORE_PROD $PRJ_INVENTORY $PRJ_CI; do
    for dc in $(oc get pods -n $project | grep deploy | grep Error | cut -d ' ' -f 1 | sed 's/\(.*\)\-[0-9]\+\-deploy/\1/g'); do
      echo "WARNING: Deployment $dc in project $project has failed. Starting a new deployment"
      oc rollout latest dc/$dc -n $project
    done
  done
}

function deploy_demo_guides() {
  echo_header "Deploying Demo Guides"
  local _DEMO_CONTENT_URL="https://raw.githubusercontent.com/osevg/workshopper-content/stable"
  local _DEMOS="$_DEMO_CONTENT_URL/demos/_demo-msa.yml,$_DEMO_CONTENT_URL/demos/_demo-agile-integration.yml"
  oc new-app --name=guides jboss-eap70-openshift~https://github.com/osevg/workshopper.git#stable -n $PRJ_CI -e WORKSHOPS_URLS=$_DEMOS -e CONTENT_URL_PREFIX=$_DEMO_CONTENT_URL -e PROJECT_SUFFIX=$PRJ_SUFFIX -e GOGS_URL=http://$GOGS_ROUTE -e GOGS_DEV_REPO_URL_PREFIX=http://$GOGS_ROUTE/$GOGS_USER/coolstore-microservice -e JENKINS_URL=http://jenkins-$PRJ_CI.$DOMAIN -e COOLSTORE_WEB_PROD_URL=http://web-ui-$PRJ_COOLSTORE_PROD.$DOMAIN -e HYSTRIX_PROD_URL=http://hystrix-dashboard-$PRJ_COOLSTORE_PROD.$DOMAIN -e GOGS_DEV_USER=$GOGS_USER -e GOGS_DEV_PASSWORD=$GOGS_PASSWORD -e GOGS_REVIEWER_USER=$GOGS_ADMIN_USER -e GOGS_REVIEWER_PASSWORD=$GOGS_ADMIN_PASSWORD -e OCP_VERSION=3.4 -n $PRJ_CI
  oc expose svc/guides -n $PRJ_CI
  oc cancel-build bc/guides -n $PRJ_CI
  oc set env bc/guides MAVEN_MIRROR_URL=$MAVEN_MIRROR_URL -n $PRJ_CI
  oc start-build guides -n $PRJ_CI
  oc set probe dc/guides -n $PRJ_CI --readiness -- /bin/bash -c /opt/eap/bin/readinessProbe.sh
  oc set probe dc/guides -n $PRJ_CI --liveness -- /bin/bash -c /opt/eap/bin/livenessProbe.sh
}

# GPTE convention
function set_default_project() {
  if [ $LOGGEDIN_USER == 'system:admin' ] ; then
    oc project default
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

if [ $LOGGEDIN_USER == 'system:admin' ] && [ $ARG_USERNAME == '' ] ; then
  echo "--user must be provided when running the script as 'system:admin'"
  echo 0
fi

if [ "$ARG_DELETE" = true ] ; then
  delete_projects
  exit 0
fi

START=`date +%s`

echo_header "Mult-product MSA Demo ($(date))"

create_infra_project 
print_info

deploy_gogs
deploy_nexus
deploy_jenkins
create_app_projects
set_project_permissions
add_inventory_template_to_projects
wait_for_nexus_to_be_ready
deploy_demo_guides
deploy_coolstore_test_env
deploy_coolstore_prod_env
deploy_inventory_dev_env
build_and_tag_images_for_ci
deploy_pipeline
sleep 30
verify_deployments

set_default_project


END=`date +%s`
echo
echo "Provisioning done! (Completed in $(( ($END - $START)/60 )) min $(( ($END - $START)%60 )) sec)"
