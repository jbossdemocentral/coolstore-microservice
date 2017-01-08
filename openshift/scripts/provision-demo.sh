#!/bin/bash
################################################################################
# Prvisioning script to deploy the demo on an OpenShift environment            #
################################################################################
function usage() {
    echo
    echo "Usage:"
    echo " $0 --user <username> [ --maven-mirror-url <value> ] [--project-suffix <value>]"
    echo " $0 --help "
    echo
    echo "Example:"
    echo " $0 --user demo --maven-mirror-url http://nexus.repo.com/content/groups/public/ --project-suffix s40d"
    echo
    echo "If --maven-mirror-url is not specified, a Nexus container will be deployed and used"
    echo "If --project-suffix is not specified, <username> will be used as the suffix"
}

ARG_USERNAME=demo
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
            ;;
        *)               # Default case: If no more options then break out of the loop.
            break
    esac

    shift
done

################################################################################
# CONFIGURATION                                                                #
################################################################################
# project
PRJ_SUFFIX=${ARG_PROJECT_SUFFIX:-`echo $ARG_USERNAME | sed -e 's/-.*//g'`}
PRJ_LABEL=demo1-$PRJ_SUFFIX
PRJ_CI=ci-$PRJ_SUFFIX
PRJ_COOLSTORE_TEST=coolstore-test-$PRJ_SUFFIX
PRJ_COOLSTORE_PROD=coolstore-prod-$PRJ_SUFFIX
PRJ_INVENTORY=inventory-test-$PRJ_SUFFIX
PRJ_DEVELOPER=developer-$PRJ_SUFFIX

# config
GITHUB_ACCOUNT=${GITHUB_ACCOUNT:-jbossdemocentral}
GITHUB_REF=${GITHUB_REF:-demo-1-gpte}
GITHUB_URI=https://github.com/$GITHUB_ACCOUNT/coolstore-microservice.git
MAVEN_MIRROR_URL=${ARG_MAVEN_MIRROR_URL:-http://nexus.$PRJ_CI.svc.cluster.local:8081/content/groups/public}
GOGS_USER=developer
GOGS_PASSWORD=developer
GOGS_ADMIN_USER=team
GOGS_ADMIN_PASSWORD=team
JENKINS_PASSWORD=openshift

################################################################################
# FUNCTIONS                                                                    #
################################################################################

function print_info() {
  echo_header "Configuration"
  echo "Project suffix:      $PRJ_SUFFIX"
  echo "Project label:       $PRJ_LABEL"
  echo "GitHub repo:         https://github.com/$GITHUB_ACCOUNT/coolstore-microservice"
  echo "GitHub branch/tag:   $GITHUB_REF"
  echo "Gogs url:            http://$GOGS_ROUTE"
  echo "Gogs admin user:     $GOGS_ADMIN_USER"
  echo "Gogs admin pwd:      $GOGS_ADMIN_PASSWORD"
  echo "Gogs user:           $GOGS_USER"
  echo "Gogs pwd:            $GOGS_PASSWORD"
  echo "Jenkins admin user:  admin"
  echo "Jenkins admin pwd:   $JENKINS_PASSWORD"
  echo "Maven mirror url:    $MAVEN_MIRROR_URL"
}

# Hack to extract domain name when it's not determine in
# advanced e.g. <user>-<project>.4s23.cluster
function set_domain_for_gogs_hack() {
  local _TEMP_PROJECT=prj$(date +%s)-$PRJ_SUFFIX
  oc new-project $_TEMP_PROJECT > /dev/null
  if [ "$(oc whoami)" == 'system:admin' ] ; then
    oc annotate --overwrite namespace $_TEMP_PROJECT demo=$PRJ_LABEL > /dev/null
  fi
  oc create route edge testroute --service=testsvc --port=80 -n $_TEMP_PROJECT >/dev/null
  DOMAIN=$(oc get route testroute -o template --template='{{.spec.host}}' -n $_TEMP_PROJECT | sed "s/testroute-$_TEMP_PROJECT.//g")
  GOGS_ROUTE="gogs-$PRJ_CI.$DOMAIN"
  oc delete route testroute -n $_TEMP_PROJECT >/dev/null
  oc delete project $_TEMP_PROJECT > /dev/null
}

function delete_projects() {
  oc delete project $PRJ_COOLSTORE_TEST $PRJ_DEVELOPER $PRJ_COOLSTORE_PROD $PRJ_INVENTORY $PRJ_CI
}

# Create Infra Project
function create_infra_project() {
  echo_header "Creating infra project..."
  oc new-project $PRJ_CI --display-name='CI/CD' --description='CI/CD Components (Jenkins, Gogs, etc)'

  if [ "$(oc whoami)" == 'system:admin' ] ; then
    oc annotate --overwrite namespace $PRJ_CI demo=$PRJ_LABEL
  fi
}

# Create Application Project
function create_app_projects() {
  echo_header "Creating application projects..."
  oc new-project $PRJ_COOLSTORE_TEST --display-name='CoolStore TEST' --description='CoolStore Test Environment'
  oc new-project $PRJ_COOLSTORE_PROD --display-name='CoolStore PROD' --description='CoolStore Production Environment'
  oc new-project $PRJ_INVENTORY --display-name='Inventory TEST' --description='Inventory Test Environment'
  oc new-project $PRJ_DEVELOPER --display-name='Developer Project' --description='Personal Developer Project'

  if [ "$(oc whoami)" == 'system:admin' ] ; then
    for project in $PRJ_COOLSTORE_TEST $PRJ_COOLSTORE_PROD $PRJ_INVENTORY $PRJ_DEVELOPER
    do
      oc annotate --overwrite namespace $project demo=$PRJ_LABEL
    done
  fi

  # join project networks
  if [ "$(oc whoami)" == 'system:admin' ] ; then
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
    echo_header "Waiting for Nexus to be ready..."
    x=1
    while [ -z "$(oc get ep nexus -o yaml -n $PRJ_CI | grep '\- addresses:')" ]
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
  fi
}

# Deploy Gogs
function deploy_gogs() {
  echo_header "Deploying Gogs git server..."

  local _TEMPLATE="https://raw.githubusercontent.com/OpenShiftDemos/gogs-openshift-docker/master/openshift/gogs-persistent-template.yaml"
  local _DB_USER=gogs
  local _DB_PASSWORD=gogs
  local _DB_NAME=gogs
  local _GITHUB_REPO="https://github.com/$GITHUB_ACCOUNT/coolstore-microservice.git"

  echo "Using template $_TEMPLATE"
  oc process -f $_TEMPLATE -v HOSTNAME=gogs-$PRJ_CI.$DOMAIN,GOGS_VERSION=0.9.113,DATABASE_USER=$_DB_USER,DATABASE_PASSWORD=$_DB_PASSWORD,DATABASE_NAME=$_DB_NAME,INSTALL_LOCK=false -n $PRJ_CI | oc create -f - -n $PRJ_CI

  echo "Waiting for Gogs to be ready..."
  x=1
  while [ -z "$(oc get ep gogs-postgresql -o yaml -n $PRJ_CI | grep '\- addresses:')" ]
  do
    echo "."
    sleep 5
    x=$(( $x + 1 ))
    if [ $x -gt 120 ]
    then
      echo "Gogs PostgreSQL still not ready, I GIVE UP!"
      exit 255
    fi
  done

  x=1
  while [ -z "$(oc get ep gogs -o yaml -n $PRJ_CI | grep '\- addresses:')" ]
  do
    echo "."
    sleep 5
    x=$(( $x + 1 ))
    if [ $x -gt 120 ]
    then
      echo "Gogs still not ready, I GIVE UP!"
      exit 255
    fi
  done
  echo "Gogs is ready!"

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
  fi

  sleep 2

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
  fi

  sleep 2

  # import tag to master
  local _CLONE_DIR=/tmp/$(date +%s)-coolstore-microservice
  rm -rf $_CLONE_DIR && \
      git clone -v http://$GOGS_ROUTE/$GOGS_ADMIN_USER/coolstore-microservice.git $_CLONE_DIR && \
      cd $_CLONE_DIR && \
      git branch -m master master-old && \
      git checkout -b master $GITHUB_REF && \
      git push -v -f http://$GOGS_ADMIN_USER:$GOGS_ADMIN_PASSWORD@$GOGS_ROUTE/$GOGS_ADMIN_USER/coolstore-microservice.git master
}

# Deploy Jenkins
function deploy_jenkins() {
  echo_header "Deploying Jenkins..."
  oc new-app jenkins-persistent -l app=jenkins -p JENKINS_PASSWORD=$JENKINS_PASSWORD -n $PRJ_CI
}

# Deploy Coolstore into Coolstore TEST project
function deploy_coolstore_test_env() {
  local _TEMPLATE_BUILDS="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/coolstore-builds-template.yaml"
  local _TEMPLATE_DEPLOYMENT="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/coolstore-deployments-template.yaml"

  echo_header "Deploying CoolStore app into $PRJ_COOLSTORE_TEST project..."
  echo "Using build template $_TEMPLATE_BUILDS"
  echo "Using deployment template $_TEMPLATE_DEPLOYMENT"

  oc process -f $_TEMPLATE_BUILDS -v GIT_URI=$GITHUB_URI -v GIT_REF=$GITHUB_REF -v MAVEN_MIRROR_URL=$MAVEN_MIRROR_URL -n $PRJ_COOLSTORE_TEST | oc create -f - -n $PRJ_COOLSTORE_TEST
  oc process -f $_TEMPLATE_DEPLOYMENT -v APP_VERSION=test -n $PRJ_COOLSTORE_TEST | oc create -f - -n $PRJ_COOLSTORE_TEST
}

# Deploy Inventory service into Inventory DEV project
function deploy_inventory_dev_env() {
  local _TEMPLATE="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/inventory-template.json"

  echo_header "Deploying Inventory service into $PRJ_INVENTORY project..."
  echo "Using template $_TEMPLATE"
  oc process -f $_TEMPLATE -v GIT_URI=http://$GOGS_ROUTE/$GOGS_ADMIN_USER/coolstore-microservice.git -v MAVEN_MIRROR_URL=$MAVEN_MIRROR_URL -n $PRJ_INVENTORY | oc create -f - -n $PRJ_INVENTORY
}

# Prepare the BuildConfigs and Deployment for CI/CD
function prepare_objects_for_ci() {
  set -x
  # wait for builds to finish
  echo_header "Preparing builds and deployments for CI/CD..."
  echo "Waiting for builds to finish..."
  for buildconfig in coolstore-gw web-ui inventory cart catalog
  do
    x=1
    while [ -z "$(oc get builds -l buildconfig=$buildconfig -n $PRJ_COOLSTORE_TEST | grep 'Complete')" ]
    do
      # if build has failed, let's give it another shot
      if [ ! -z "$(oc get builds -l buildconfig=$buildconfig -n $PRJ_COOLSTORE_TEST | grep 'Failed')" ] && [ -z "_BUILD_RETRIED_$buildconfig" ]
      then
        oc start-build $buildconfig -n $PRJ_COOLSTORE_TEST
        eval local _BUILD_RETRIED_$buildconfig=true
      fi

      echo "."
      sleep 10
      x=$(( $x + 1 ))
      if [ $x -gt 120 ]
      then
        echo "Tired of waiting for builds to finish, I give up!"
        exit 255
      fi
    done

    echo "Build $buildconfig completed"
  done

  # remove buildconfigs. Jenkins does that!
  oc delete bc --all -n $PRJ_COOLSTORE_TEST

  for is in coolstore-gw web-ui inventory cart catalog
  do
    # tag images in test
    oc tag $PRJ_COOLSTORE_TEST/$is:latest $PRJ_COOLSTORE_TEST/$is:test
    # tag images in prod
    oc tag $PRJ_COOLSTORE_TEST/$is:latest $PRJ_COOLSTORE_PROD/$is:prod
    # remove latest tag
    oc tag $PRJ_COOLSTORE_TEST/$is:latest -d
  done

  # remove fis image
  oc delete is fis-java-openshift -n $PRJ_COOLSTORE_TEST

  set +x
}

function set_permissions() {
  oc adm policy add-role-to-user admin $ARG_USERNAME -n $PRJ_COOLSTORE_TEST
  oc adm policy add-role-to-user admin $ARG_USERNAME -n $PRJ_DEVELOPER
  oc adm policy add-role-to-user admin $ARG_USERNAME -n $PRJ_COOLSTORE_PROD
  oc adm policy add-role-to-user admin $ARG_USERNAME -n $PRJ_INVENTORY
  oc adm policy add-role-to-user admin $ARG_USERNAME -n $PRJ_CI

  oc adm policy add-role-to-user admin system:serviceaccounts:$PRJ_CI -n $PRJ_COOLSTORE_TEST
  oc adm policy add-role-to-user admin system:serviceaccounts:$PRJ_CI -n $PRJ_DEVELOPER
  oc adm policy add-role-to-user admin system:serviceaccounts:$PRJ_CI -n $PRJ_COOLSTORE_PROD
  oc adm policy add-role-to-user admin system:serviceaccounts:$PRJ_CI -n $PRJ_INVENTORY
}

# GPTE convention
function set_default_project() {
  if [ "$(oc whoami)" == 'system:admin' ] ; then
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
if [ "$ARG_DELETE" = true ] ; then
  delete_projects
  exit 0
fi

START=`date +%s`

set_domain_for_gogs_hack
print_info
create_infra_project
deploy_gogs
deploy_nexus
deploy_jenkins
create_app_projects
add_inventory_template_to_projects
wait_for_nexus_to_be_ready
deploy_coolstore_test_env
deploy_inventory_dev_env
set_default_project
set_permissions
prepare_objects_for_ci

END=`date +%s`
echo
echo "Provisioning done! (Completed in $(( ($END - $START)/60 )) min $(( ($END - $START)%60 )) sec)"
