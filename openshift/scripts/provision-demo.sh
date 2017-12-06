#!/bin/bash
################################################################################
# Prvisioning script to deploy the demo on an OpenShift environment            #
################################################################################
function usage() {
    echo
    echo "Usage:"
    echo " $0 [command] [demo-name] [options]"
    echo " $0 --help"
    echo
    echo "Example:"
    echo " $0 deploy --maven-mirror-url http://nexus.repo.com/content/groups/public/ --project-suffix mydemo"
    echo
    echo "COMMANDS:"
    echo "   deploy                   Set up the demo projects and deploy demo apps"
    echo "   delete                   Clean up and remove demo projects and objects"
    echo "   verify                   Verify the demo is deployed correctly"
    echo "   idle                     Make all demo servies idle"
    echo "   unidle                   Make all demo servies unidle"
    echo 
    echo "DEMOS:"
    echo "   msa                      Microservices app with all services" 
    echo "   msa-min                  Microservices app with minimum services" 
    echo "   msa-cicd-eap             CI/CD and microservices with JBoss EAP (dev-test-prod)"
    echo "   msa-cicd-eap-min         CI/CD and microservices with JBoss EAP with minimum services (dev-prod)"
    echo "   agile-integration        Agile integration, fault tolerance and CI/CD for integration (dev-test-prod)"
    echo "   agile-integration-min    Agile integration, fault tolerance and CI/CD for integration with minimal services (dev-prod)"
    echo
    echo "OPTIONS:"
    echo "   --user [username]         The admin user for the demo projects. mandatory if logged in as system:admin"
    echo "   --maven-mirror-url [url]  Use the given Maven repository for builds. If not specifid, a Nexus container is deployed in the demo"
    echo "   --project-suffix [suffix] Suffix to be added to demo project names e.g. ci-SUFFIX. If empty, user will be used as suffix"
    echo "   --ephemeral               Deploy demo without persistent storage"
    echo "   --run-verify              Run verify after provisioning"
    echo "   --keep-builds             Keep the coolstore build configs after builds are complete"
    echo
}

ARG_USERNAME=
ARG_PROJECT_SUFFIX=
ARG_MAVEN_MIRROR_URL=
ARG_EPHEMERAL=false
ARG_COMMAND=
ARG_RUN_VERIFY=false
ARG_DEMO=
ARG_DELETE_BUILDS=true

while :; do
    case $1 in
        deploy)
            ARG_COMMAND=deploy
            if [ -n "$2" ]; then
                ARG_DEMO=$2
                shift
            fi
            ;;
        delete)
            ARG_COMMAND=delete
            if [ -n "$2" ]; then
                ARG_DEMO=$2
                shift
            fi
            ;;
        verify)
            ARG_COMMAND=verify
            if [ -n "$2" ]; then
                ARG_DEMO=$2
                shift
            fi
            ;;
        idle)
            ARG_COMMAND=idle
            if [ -n "$2" ]; then
                ARG_DEMO=$2
                shift
            fi
            ;;
        unidle)
            ARG_COMMAND=unidle
            if [ -n "$2" ]; then
                ARG_DEMO=$2
                shift
            fi
            ;;
        --user)
            if [ -n "$2" ]; then
                ARG_USERNAME=$2
                shift
            else
                printf 'ERROR: "--user" requires a non-empty value.\n' >&2
                usage
                exit 255
            fi
            ;;
        --maven-mirror-url)
            if [ -n "$2" ]; then
                ARG_MAVEN_MIRROR_URL=$2
                shift
            else
                printf 'ERROR: "--maven-mirror-url" requires a non-empty value.\n' >&2
                usage
                exit 255
            fi
            ;;
        --project-suffix)
            if [ -n "$2" ]; then
                ARG_PROJECT_SUFFIX=$2
                shift
            else
                printf 'ERROR: "--project-suffix" requires a non-empty value.\n' >&2
                usage
                exit 255
            fi
            ;;
        --minimal)
            printf 'WARNING: --minimal is deprecated. Specify the demo name to deploy a subset of pods.\n' >&2
            usage
            exit 255
            ;;
        --ephemeral)
            ARG_EPHEMERAL=true
            ;;
        --run-verify)
            ARG_RUN_VERIFY=true
            ;;
        --keep-builds)
            ARG_DELETE_BUILDS=false
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        --)
            shift
            break
            ;;
        -?*)
            printf 'WARN: Unknown option (ignored): %s\n' "$1" >&2
            shift
            ;;
        *) # Default case: If no more options then break out of the loop.
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
PRJ_CI=("ci-$PRJ_SUFFIX" "CI/CD" "CI/CD Components (Jenkins, Gogs, etc)")
PRJ_COOLSTORE_TEST=("coolstore-test-$PRJ_SUFFIX" "CoolStore TEST" "CoolStore Test Environment")
PRJ_COOLSTORE_PROD=("coolstore-prod-$PRJ_SUFFIX" "CoolStore PROD" "CoolStore Production Environment")
PRJ_SERVICE_DEV=("inventory-dev-$PRJ_SUFFIX" "Inventory DEV" "Inventory DEV Environment")
PRJ_DEVELOPER=("developer-$PRJ_SUFFIX" "Developer Project" "Personal Developer Project")

PRJ_CI=ci-$PRJ_SUFFIX
PRJ_COOLSTORE_TEST=coolstore-test-$PRJ_SUFFIX
PRJ_COOLSTORE_PROD=coolstore-prod-$PRJ_SUFFIX
PRJ_DEVELOPER=developer-$PRJ_SUFFIX

# config
GITHUB_ACCOUNT=${GITHUB_ACCOUNT:-jbossdemocentral}
GITHUB_REF=${GITHUB_REF:-master}
GITHUB_URI=https://github.com/$GITHUB_ACCOUNT/coolstore-microservice.git
COOLSTORE_IMAGES_NAMESPACE=${COOLSTORE_IMAGES_NAMESPACE:-coolstore-builds}

# maven 
MAVEN_MIRROR_URL=${ARG_MAVEN_MIRROR_URL:-http://nexus.${PRJ_CI[0]}.svc.cluster.local:8081/content/groups/public}

GOGS_USER=developer
GOGS_PASSWORD=developer
GOGS_ADMIN_USER=team
GOGS_ADMIN_PASSWORD=team

WEBHOOK_SECRET=UfW7gQ6Jx4

################################################################################
# DEMO MATRIX                                                                  #
################################################################################
ENABLE_CI_CD=false
ENABLE_TEST_ENV=false
SCALE_DOWN_PROD=false
WORKSHOP_YAML=demo-cicd-eap.yml

case $ARG_DEMO in
    msa)
        WORKSHOP_YAML=demo-msa.yml
        MAVEN_MIRROR_URL=$ARG_MAVEN_MIRROR_URL
        PRJ_COOLSTORE_PROD=("coolstore-$PRJ_SUFFIX" "CoolStore" "CoolStore Microservice Application")
        PRJ_CI=("coolstore-$PRJ_SUFFIX" "CoolStore" "CoolStore Microservice Application")
        ;;
    msa-min)
        SCALE_DOWN_PROD=true
        WORKSHOP_YAML=demo-msa-min.yml
        MAVEN_MIRROR_URL=$ARG_MAVEN_MIRROR_URL
        PRJ_COOLSTORE_PROD=("coolstore-$PRJ_SUFFIX" "CoolStore" "CoolStore Microservice Application")
        PRJ_CI=("coolstore-$PRJ_SUFFIX" "CoolStore" "CoolStore Microservice Application")
        ;;
    msa-cicd-eap)
        ENABLE_CI_CD=true
        ENABLE_TEST_ENV=true
        WORKSHOP_YAML=demo-cicd-eap.yml
        MAVEN_MIRROR_URL=${ARG_MAVEN_MIRROR_URL:-http://nexus.${PRJ_CI[0]}.svc.cluster.local:8081/content/groups/public}
        ;;
    msa-cicd-eap-min)
        ENABLE_CI_CD=true
        SCALE_DOWN_PROD=true
        WORKSHOP_YAML=demo-cicd-eap-min.yml
        MAVEN_MIRROR_URL=${ARG_MAVEN_MIRROR_URL:-http://nexus.${PRJ_CI[0]}.svc.cluster.local:8081/content/groups/public}
        ;;
    agile-integration)
        ENABLE_CI_CD=true
        WORKSHOP_YAML=demo-agile-integration.yml
        PRJ_SERVICE_DEV=("gateway-dev-$PRJ_SUFFIX" "Gateway DEV" "Gateway DEV Environment")
        MAVEN_MIRROR_URL=${ARG_MAVEN_MIRROR_URL:-http://nexus.${PRJ_CI[0]}.svc.cluster.local:8081/content/groups/public}
        ;;
    agile-integration-min)
        ENABLE_CI_CD=true
        SCALE_DOWN_PROD=true
        WORKSHOP_YAML=demo-agile-integration-min.yml
        PRJ_SERVICE_DEV=("gateway-dev-$PRJ_SUFFIX" "Gateway DEV" "Gateway DEV Environment")
        MAVEN_MIRROR_URL=${ARG_MAVEN_MIRROR_URL:-http://nexus.${PRJ_CI[0]}.svc.cluster.local:8081/content/groups/public}
        ;;
    *)
        echo "ERROR: Invalid demo name: \"$ARG_DEMO\""
        usage
        exit 255
        ;;
esac

################################################################################
# FUNCTIONS                                                                    #
################################################################################

function print_info() {
  echo_header "Configuration"

  OPENSHIFT_MASTER=$(oc whoami --show-server)

  echo "Demo name:           $ARG_DEMO"
  echo "OpenShift master:    $OPENSHIFT_MASTER"
  echo "Current user:        $LOGGEDIN_USER"
  echo "Project owner:       $OPENSHIFT_USER"
  echo "Project suffix:      $PRJ_SUFFIX"
  echo "Ephemeral:           $ARG_EPHEMERAL"
  echo "GitHub repo:         $GITHUB_URI"
  echo "GitHub branch/tag:   $GITHUB_REF"

  if [ "$ENABLE_CI_CD" = true ] && [ "$ARG_COMMAND" == "deploy" ] ; then
    echo "Maven mirror url:    $MAVEN_MIRROR_URL"
    echo "Gogs url:            http://$GOGS_ROUTE"
    echo "Gogs admin user:     $GOGS_ADMIN_USER"
    echo "Gogs admin pwd:      $GOGS_ADMIN_PASSWORD"
    echo "Gogs user:           $GOGS_USER"
    echo "Gogs pwd:            $GOGS_PASSWORD"
  fi
}

function remove_storage_claim() {
  local _DC=$1
  local _VOLUME_NAME=$2
  local _CLAIM_NAME=$3
  local _PROJECT=$4
  oc volumes dc/$_DC --name=$_VOLUME_NAME --add -t emptyDir --overwrite -n $_PROJECT
  oc delete pvc $_CLAIM_NAME -n $_PROJECT >/dev/null 2>&1
}

function configure_project_permissions() {
  _PROJECTS=$@
  for project in $_PROJECTS
  do
    oc adm policy add-role-to-group admin system:serviceaccounts:${PRJ_CI[0]} -n $project >/dev/null 2>&1
    oc adm policy add-role-to-group admin system:serviceaccounts:$project -n $project >/dev/null 2>&1
  done

  if [ $LOGGEDIN_USER == 'system:admin' ] ; then
    for project in $_PROJECTS
    do
      oc adm policy add-role-to-user admin $ARG_USERNAME -n $project >/dev/null 2>&1
      oc annotate --overwrite namespace $project demo=demo1-$PRJ_SUFFIX demo=demo-modern-arch-$PRJ_SUFFIX >/dev/null 2>&1
    done
    oc adm pod-network join-projects --to=${PRJ_CI[0]} $_PROJECTS >/dev/null 2>&1
  fi

  # Hack to extract domain name when it's not determine in
  # advanced e.g. <user>-<project>.4s23.cluster
  oc create route edge testroute --service=testsvc --port=80 -n ${PRJ_COOLSTORE_PROD[0]} >/dev/null
  DOMAIN=$(oc get route testroute -o template --template='{{.spec.host}}' -n ${PRJ_COOLSTORE_PROD[0]} | sed "s/testroute-${PRJ_COOLSTORE_PROD[0]}.//g")
  GOGS_ROUTE="gogs-${PRJ_CI[0]}.$DOMAIN"
  oc delete route testroute -n ${PRJ_COOLSTORE_PROD[0]} >/dev/null
}

# Create Infra Project for CI/CD
function create_cicd_projects() {
  echo_header "Creating project..."

  echo "Creating project ${PRJ_CI[0]}"
  oc new-project ${PRJ_CI[0]} --display-name="${PRJ_CI[1]}" --description="${PRJ_CI[2]}" >/dev/null
  echo "Creating project ${PRJ_COOLSTORE_PROD[0]}"
  oc new-project ${PRJ_COOLSTORE_PROD[0]} --display-name="${PRJ_COOLSTORE_PROD[1]}" --description="${PRJ_COOLSTORE_PROD[2]}" >/dev/null
  echo "Creating project ${PRJ_SERVICE_DEV[0]}"
  oc new-project ${PRJ_SERVICE_DEV[0]} --display-name="${PRJ_SERVICE_DEV[1]}" --description="${PRJ_SERVICE_DEV[2]}" >/dev/null

  if [ "$ENABLE_TEST_ENV" = true ] ; then
    echo "Creating project ${PRJ_COOLSTORE_TEST[0]}"
    oc new-project ${PRJ_COOLSTORE_TEST[0]} --display-name="${PRJ_COOLSTORE_TEST[1]}" --description="${PRJ_COOLSTORE_TEST[2]}" >/dev/null
    echo "Creating project ${PRJ_DEVELOPER[0]}"
    oc new-project ${PRJ_DEVELOPER[0]} --display-name="${PRJ_DEVELOPER[1]}" --description="${PRJ_DEVELOPER[2]}" >/dev/null
  fi

  configure_project_permissions ${PRJ_CI[0]} ${PRJ_COOLSTORE_TEST[0]} ${PRJ_COOLSTORE_PROD[0]} ${PRJ_SERVICE_DEV[0]} ${PRJ_DEVELOPER[0]}
}

# Create Project
function create_projects() {
  echo_header "Creating project..."

  echo "Creating project ${PRJ_COOLSTORE_PROD[0]}"
  oc new-project ${PRJ_COOLSTORE_PROD[0]} --display-name="${PRJ_COOLSTORE_PROD[1]}" --description="${PRJ_COOLSTORE_PROD[2]}" >/dev/null

  configure_project_permissions ${PRJ_COOLSTORE_PROD[0]}
}

# Add Service Template
function add_service_templates_to_projects() {
  echo_header "Adding inventory template to ${PRJ_DEVELOPER[0]} project"
  local _TEMPLATE=https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/inventory-template.json
  curl -sL $_TEMPLATE | tr -d '\n' | tr -s '[:space:]' \
    | sed "s|\"MAVEN_MIRROR_URL\", \"value\": \"\"|\"MAVEN_MIRROR_URL\", \"value\": \"$MAVEN_MIRROR_URL\"|g" \
    | sed "s|\"https://github.com/jbossdemocentral/coolstore-microservice\"|\"http://$GOGS_ROUTE/$GOGS_USER/coolstore-microservice.git\"|g" \
    | oc create -f - -n ${PRJ_DEVELOPER[0]}
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
    oc process -f $_TEMPLATE -n ${PRJ_CI[0]} | oc create -f - -n ${PRJ_CI[0]}
    sleep 5
    oc set resources dc/nexus --limits=cpu=1,memory=2Gi --requests=cpu=200m,memory=1Gi -n ${PRJ_CI[0]}
  else
    echo_header "Using existng Maven mirror: $ARG_MAVEN_MIRROR_URL"
  fi
}

# Wait till Nexus is ready
function wait_for_nexus_to_be_ready() {
  if [ -z "$ARG_MAVEN_MIRROR_URL" ] ; then # no maven mirror specified
    oc rollout status dc nexus -n ${PRJ_CI[0]}
  fi
}

# Deploy Gogs
function deploy_gogs() {
  echo_header "Deploying Gogs git server..."
  
  local _TEMPLATE="https://raw.githubusercontent.com/OpenShiftDemos/gogs-openshift-docker/rpm/openshift/gogs-persistent-template.yaml"
  if [ "$ARG_EPHEMERAL" = true ] ; then
    _TEMPLATE="https://raw.githubusercontent.com/OpenShiftDemos/gogs-openshift-docker/rpm/openshift/gogs-template.yaml"
  fi

  local _DB_USER=gogs
  local _DB_PASSWORD=gogs
  local _DB_NAME=gogs

  echo "Using template $_TEMPLATE"
  oc process -f $_TEMPLATE --param=HOSTNAME=$GOGS_ROUTE --param=GOGS_VERSION=0.9.113 --param=DATABASE_USER=$_DB_USER --param=DATABASE_PASSWORD=$_DB_PASSWORD --param=DATABASE_NAME=$_DB_NAME --param=SKIP_TLS_VERIFY=true -n ${PRJ_CI[0]} | oc create -f - -n ${PRJ_CI[0]}

  sleep 5

  # wait for Gogs to be ready
  oc rollout status dc gogs-postgresql -n ${PRJ_CI[0]}
  oc rollout status dc gogs -n ${PRJ_CI[0]}

  sleep 10

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
  "name": "coolstore-microservice",
  "private": false
}
EOM

  _RETURN=$(curl -o /dev/null -sL -w "%{http_code}" -H "Content-Type: application/json" -d "$_DATA_JSON" -u $GOGS_ADMIN_USER:$GOGS_ADMIN_PASSWORD -X POST http://$GOGS_ROUTE/api/v1/user/repos)
  if [ $_RETURN != "201" ] && [ $_RETURN != "200" ] ; then
    echo "WARNING: Failed (http code $_RETURN) to create repository"
  else
    echo "CoolStore repo created"
  fi

  sleep 2

  local _REPO_DIR=/tmp/$(date +%s)-coolstore-microservice
  pushd ~ >/dev/null && \
      rm -rf $_REPO_DIR && \
      mkdir $_REPO_DIR && \
      cd $_REPO_DIR && \
      git init && \
      curl -sL -o ./coolstore.zip https://github.com/$GITHUB_ACCOUNT/coolstore-microservice/archive/$GITHUB_REF.zip && \
      unzip coolstore.zip && \
      mv coolstore-microservice-$GITHUB_REF/* . && \
      rm -rf coolstore.zip coolstore-microservice-$GITHUB_REF && \
      git remote add origin http://$GOGS_ROUTE/$GOGS_ADMIN_USER/coolstore-microservice.git && \
      git add . --all && \
      git config user.email "rileylenard@redhat.com" && \
      git config user.name "Riley Lenard" && \
      git commit -m "Initial add" && \
      git push -f http://$GOGS_ADMIN_USER:$GOGS_ADMIN_PASSWORD@$GOGS_ROUTE/$GOGS_ADMIN_USER/coolstore-microservice.git master && \
      popd >/dev/null && \
      rm -rf $_REPO_DIR

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
  else
    echo "Gogs user created: $GOGS_USER"
  fi
}

# Deploy Jenkins
function deploy_jenkins() {
  echo_header "Deploying Jenkins..."
  oc new-app jenkins-ephemeral -l app=jenkins -p MEMORY_LIMIT=1Gi --param=JENKINS_IMAGE_STREAM_TAG=v3.7 -n ${PRJ_CI[0]}
  sleep 2
  oc set resources dc/jenkins --limits=cpu=1,memory=2Gi --requests=cpu=200m,memory=1Gi -n ${PRJ_CI[0]}
}

function remove_coolstore_storage_if_ephemeral() {
  local _PROJECT=$1
  if [ "$ARG_EPHEMERAL" = true ] ; then
    remove_storage_claim inventory-postgresql inventory-postgresql-data inventory-postgresql-pv $_PROJECT
    remove_storage_claim catalog-mongodb mongodb-data catalog-mongodb-pv $_PROJECT
    remove_storage_claim rating-mongodb mongodb-data rating-mongodb-pv $_PROJECT
    remove_storage_claim review-postgresql review-postgresql-data review-postgresql-pv $_PROJECT
  fi
}

function scale_down_deployments_by_labels() {
  local _project=$1
  local _selector=$2
  local _deployments=$(oc get dc -l $_selector -o=custom-columns=:.metadata.name -n $_project)

  for _dc in $_deployments; do
      oc rollout cancel dc/$_dc -n $_project 2>/dev/null
      oc scale --replicas=0 dc $_dc -n $_project
  done
}

# Deploy Coolstore into Coolstore TEST project
function deploy_coolstore_test_env() {
  local _TEMPLATE="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/coolstore-deployments-template.yaml"

  echo_header "Deploying CoolStore app into ${PRJ_COOLSTORE_TEST[0]} project..."
  echo "Using deployment template $_TEMPLATE"
  oc process -f $_TEMPLATE --param=APP_VERSION=test --param=HOSTNAME_SUFFIX=${PRJ_COOLSTORE_TEST[0]}.$DOMAIN -n ${PRJ_COOLSTORE_TEST[0]} | oc create -f - -n ${PRJ_COOLSTORE_TEST[0]}
  
  sleep 2
  scale_down_deployments_by_labels ${PRJ_COOLSTORE_TEST[0]} comp-required!=true,app!=inventory

  sleep 2
  remove_coolstore_storage_if_ephemeral ${PRJ_COOLSTORE_TEST[0]}
}

# Configure Blue-Green Deployment for Inventory in PROD project
function configure_bluegreen_in_prod() {
  local _TEMPLATE_BLUEGREEN="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/inventory-bluegreen-template.yaml"

  echo_header "Configuring blue/green deployments in ${PRJ_COOLSTORE_PROD[0]} project..."
  echo "Using bluegreen template $_TEMPLATE_BLUEGREEN"

  oc delete all,pvc -l app=inventory --now --ignore-not-found -n ${PRJ_COOLSTORE_PROD[0]}
  oc process -f $_TEMPLATE_BLUEGREEN --param=APP_VERSION_BLUE=prod-blue --param=APP_VERSION_GREEN=prod-green --param=HOSTNAME_SUFFIX=${PRJ_COOLSTORE_PROD[0]}.$DOMAIN -n ${PRJ_COOLSTORE_PROD[0]} | oc create -f - -n ${PRJ_COOLSTORE_PROD[0]}
  sleep 2
  remove_coolstore_storage_if_ephemeral ${PRJ_COOLSTORE_PROD[0]}
}

# Deploy Coolstore into Coolstore PROD project
function deploy_coolstore_prod_env() {
  local _TEMPLATE_PREFIX="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates"
  local _TEMPLATE_DEPLOYMENT="$_TEMPLATE_PREFIX/coolstore-deployments-template.yaml"
  local _TEMPLATE_NETFLIX="$_TEMPLATE_PREFIX/netflix-oss-list.yaml"

  sleep 10

  echo_header "Deploying CoolStore app into ${PRJ_COOLSTORE_PROD[0]} project..."
  echo "Using deployment template $_TEMPLATE_DEPLOYMENT"
  echo "Using Netflix OSS template $_TEMPLATE_NETFLIX"

  local _APP_VERSION=latest
  if [ "$ENABLE_CI_CD" = true ] ; then
     _APP_VERSION=prod
  fi

  oc process -f $_TEMPLATE_DEPLOYMENT --param=APP_VERSION=$_APP_VERSION --param=HOSTNAME_SUFFIX=${PRJ_COOLSTORE_PROD[0]}.$DOMAIN -n ${PRJ_COOLSTORE_PROD[0]} | oc create -f - -n ${PRJ_COOLSTORE_PROD[0]}
  oc create -f $_TEMPLATE_NETFLIX -n ${PRJ_COOLSTORE_PROD[0]}
  
  remove_coolstore_storage_if_ephemeral ${PRJ_COOLSTORE_PROD[0]}

  # driven by the demo type
  if [ "$SCALE_DOWN_PROD" = true ] ; then
    scale_down_deployments_by_labels ${PRJ_COOLSTORE_PROD[0]} comp-required!=true
   fi  
}

# Deploy service into Inventory DEV project
function deploy_service_dev_env() {
  local _TEMPLATE="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/inventory-template.json"

  echo_header "Deploying Inventory service into ${PRJ_SERVICE_DEV[0]} project..."
  echo "Using template $_TEMPLATE"
  oc process -f $_TEMPLATE --param=GIT_URI=http://$GOGS_ROUTE/$GOGS_ADMIN_USER/coolstore-microservice.git --param=GIT_REF=master --param=MAVEN_MIRROR_URL=$MAVEN_MIRROR_URL -n ${PRJ_SERVICE_DEV[0]} | oc create -f - -n ${PRJ_SERVICE_DEV[0]}
  sleep 2
}

function images_exists() {
  # check if images project exist
  oc get project $COOLSTORE_IMAGES_NAMESPACE > /dev/null 2>&1
  if [ ! $? -eq 0 ]; then
    return 1
  fi

  # check if all images exist
  for buildconfig in web-ui inventory cart catalog coolstore-gw pricing rating review
  do
    oc get bc $buildconfig -n $COOLSTORE_IMAGES_NAMESPACE > /dev/null 2>&1
    if [ ! $? -eq 0 ]; then
      return 1
    fi
  done

  return 0
}

function build_images() {
  local _TEMPLATE_BUILDS="https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/coolstore-builds-template.yaml"
  echo "Using build template $_TEMPLATE_BUILDS"
  oc process -f $_TEMPLATE_BUILDS --param=GIT_URI=$GITHUB_URI --param=GIT_REF=$GITHUB_REF --param=MAVEN_MIRROR_URL=$MAVEN_MIRROR_URL -n ${PRJ_COOLSTORE_PROD[0]} | oc create -f - -n ${PRJ_COOLSTORE_PROD[0]}

  sleep 10

  # build images
  for buildconfig in web-ui inventory cart catalog coolstore-gw pricing rating review
  do
    oc start-build $buildconfig -n ${PRJ_COOLSTORE_PROD[0]} --wait
  done
}

function promote_images() {
  echo_header "Promoting Images ..."

  # remove buildconfigs
  if [ "$ARG_DELETE_BUILDS" = true ] ; then
    oc delete bc --all -n ${PRJ_COOLSTORE_PROD[0]}
  fi

  for is in coolstore-gw web-ui cart catalog pricing rating review
  do
    
    if [ "$ENABLE_TEST_ENV" = true ] ; then
      oc tag ${PRJ_COOLSTORE_PROD[0]}/$is:latest ${PRJ_COOLSTORE_TEST[0]}/$is:test
    fi
    oc tag ${PRJ_COOLSTORE_PROD[0]}/$is:latest ${PRJ_COOLSTORE_PROD[0]}/$is:prod
    oc tag ${PRJ_COOLSTORE_PROD[0]}/$is:latest -d
  done

  oc tag ${PRJ_COOLSTORE_PROD[0]}/inventory:latest ${PRJ_SERVICE_DEV[0]}/inventory:latest

  if [ "$ENABLE_TEST_ENV" = true ] ; then
    oc tag ${PRJ_COOLSTORE_PROD[0]}/inventory:latest ${PRJ_COOLSTORE_TEST[0]}/inventory:test
  fi

  oc tag ${PRJ_COOLSTORE_PROD[0]}/inventory:latest ${PRJ_COOLSTORE_PROD[0]}/inventory:prod-green
  oc tag ${PRJ_COOLSTORE_PROD[0]}/inventory:latest ${PRJ_COOLSTORE_PROD[0]}/inventory:prod-blue
  oc tag ${PRJ_COOLSTORE_PROD[0]}/inventory:latest -d
}

function import_images() {
  for is in coolstore-gw web-ui cart catalog pricing rating review inventory
  do
    oc tag $COOLSTORE_IMAGES_NAMESPACE/$is:latest ${PRJ_COOLSTORE_PROD[0]}/$is:latest
  done
}

function deploy_pipeline() {
  echo_header "Configuring CI/CD..."

  local _PIPELINE_NAME=inventory-pipeline
  

  if [ "$ENABLE_TEST_ENV" = true ] ; then
    local _TEMPLATE=https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/inventory-pipeline-template.yaml
    oc process -f $_TEMPLATE --param=PIPELINE_NAME=$_PIPELINE_NAME --param=DEV_PROJECT=${PRJ_SERVICE_DEV[0]} --param=TEST_PROJECT=${PRJ_COOLSTORE_TEST[0]} --param=PROD_PROJECT=${PRJ_COOLSTORE_PROD[0]} --param=GENERIC_WEBHOOK_SECRET=$WEBHOOK_SECRET -n ${PRJ_CI[0]} | oc create -f - -n ${PRJ_CI[0]}
  else
    local _TEMPLATE=https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/templates/inventory-pipeline-template-simple.yaml
    oc process -f $_TEMPLATE --param=PIPELINE_NAME=$_PIPELINE_NAME --param=DEV_PROJECT=${PRJ_SERVICE_DEV[0]} --param=PROD_PROJECT=${PRJ_COOLSTORE_PROD[0]} --param=GENERIC_WEBHOOK_SECRET=$WEBHOOK_SECRET -n ${PRJ_CI[0]} | oc create -f - -n ${PRJ_CI[0]}
  fi

  # configure webhook to trigger pipeline
  read -r -d '' _DATA_JSON << EOM
{
  "type": "gogs",
  "config": {
    "url": "$OPENSHIFT_MASTER/oapi/v1/namespaces/${PRJ_CI[0]}/buildconfigs/$_PIPELINE_NAME/webhooks/$WEBHOOK_SECRET/generic",
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
  echo "Verifying builds..."
  local _BUILDS_FAILED=false
  for buildconfig in coolstore-gw web-ui inventory cart catalog pricing rating review
  do
    if [ -n "$(oc get builds -n ${PRJ_COOLSTORE_PROD[0]} | grep $buildconfig | grep Failed)" ] && [ -z "$(oc get builds -n ${PRJ_COOLSTORE_PROD[0]} | grep $buildconfig | grep Complete)" ]; then
      _BUILDS_FAILED=true
      echo "WARNING: Build $project/$buildconfig: FAILED"
      echo
      echo "Starting a new build for $project/$buildconfig ..."
      echo
      oc start-build $buildconfig -n ${PRJ_COOLSTORE_PROD[0]} --wait
    fi
  done

  # promote images if builds had failed
  if [ "$_BUILDS_FAILED" = true ] && [ "$ENABLE_CI_CD" = true ] ; then
    promote_images
    deploy_pipeline
  fi

  echo "Verifying deployments..."
  # verify and retry deployments
  if [ "$ENABLE_CI_CD" = true ] ; then
    verify_deployments_in_projects ${PRJ_COOLSTORE_PROD[0]} ${PRJ_CI[0]} ${PRJ_SERVICE_DEV[0]}

    if [ "$ENABLE_TEST_ENV" = true ] ; then
      verify_deployments_in_projects ${PRJ_COOLSTORE_TEST[0]}
    fi

  else
    verify_deployments_in_projects ${PRJ_COOLSTORE_PROD[0]} ${PRJ_CI[0]}
  fi 
}

function verify_deployments_in_projects() {
  for project in "$@"
  do
    local deployments="$(oc get dc -l comp-type=database -n $project -o=custom-columns=:.metadata.name 2>/dev/null) $(oc get dc -l comp-type!=database -n $project -o=custom-columns=:.metadata.name 2>/dev/null)"
    for dc in $deployments; do
      dc_status=$(oc get dc $dc -n $project -o=custom-columns=:.spec.replicas,:.status.availableReplicas)
      dc_replicas=$(echo $dc_status | sed "s/^\([0-9]\+\) \([0-9]\+\)$/\1/")
      dc_available=$(echo $dc_status | sed "s/^\([0-9]\+\) \([0-9]\+\)$/\2/")

      if [ "$dc_available" -lt "$dc_replicas" ] ; then
        echo "WARNING: Deployment $project/$dc: FAILED"
        echo
        echo "Starting a new deployment for $project/$dc ..."
        echo
        oc rollout cancel dc/$dc -n $project >/dev/null
        sleep 5
        oc rollout latest dc/$dc -n $project
        oc rollout status dc/$dc -n $project
      else
        echo "Deployment $project/$dc: OK"
      fi
    done
  done
}

function deploy_guides() {
  echo_header "Deploying Demo Guides"

  local _DEMO_CONTENT_URL_PREFIX="https://raw.githubusercontent.com/osevg/workshopper-content/master"
  local _DEMO_URLS="$_DEMO_CONTENT_URL_PREFIX/_workshops/$WORKSHOP_YAML"

  oc new-app --name=guides --docker-image=osevg/workshopper:latest -n ${PRJ_CI[0]} \
      -e WORKSHOPS_URLS=$_DEMO_URLS \
      -e CONTENT_URL_PREFIX=$_DEMO_CONTENT_URL_PREFIX \
      -e PROJECT_SUFFIX=$PRJ_SUFFIX \
      -e GOGS_URL=http://$GOGS_ROUTE \
      -e GOGS_DEV_REPO_URL_PREFIX=http://$GOGS_ROUTE/$GOGS_USER/coolstore-microservice \
      -e JENKINS_URL=http://jenkins-${PRJ_CI[0]}.$DOMAIN \
      -e COOLSTORE_WEB_PROD_URL=http://web-ui-${PRJ_COOLSTORE_PROD[0]}.$DOMAIN \
      -e HYSTRIX_PROD_URL=http://hystrix-dashboard-${PRJ_COOLSTORE_PROD[0]}.$DOMAIN \
      -e GOGS_DEV_USER=$GOGS_USER -e GOGS_DEV_PASSWORD=$GOGS_PASSWORD \
      -e GOGS_REVIEWER_USER=$GOGS_ADMIN_USER \
      -e GOGS_REVIEWER_PASSWORD=$GOGS_ADMIN_PASSWORD \
      -e OCP_VERSION=3.5 -n ${PRJ_CI[0]}
  oc expose svc/guides -n ${PRJ_CI[0]}
  oc set probe dc/guides -n ${PRJ_CI[0]} --readiness --liveness --get-url=http://:8080/ --failure-threshold=5 --initial-delay-seconds=30
  oc set resources dc/guides --limits=cpu=500m,memory=1Gi --requests=cpu=100m,memory=512Mi -n ${PRJ_CI[0]}
}

function make_idle() {
  echo_header "Idling Services"
  oc idle -n ${PRJ_CI[0]} --all
  oc idle -n ${PRJ_COOLSTORE_TEST[0]} --all
  oc idle -n ${PRJ_COOLSTORE_PROD[0]} --all
  oc idle -n ${PRJ_SERVICE_DEV[0]} --all
  oc idle -n ${PRJ_DEVELOPER[0]} --all
}

function make_unidle() {
  echo_header "Unidling Services"
  local _DIGIT_REGEX="^[[:digit:]]*$"

  for project in $PRJ_COOLSTORE_PROD $PRJ_COOLSTORE_TEST $PRJ_CI $PRJ_INVENTORY $PRJ_DEVELOPER
  do
    for dc in $(oc get dc -n $project -o=custom-columns=:.metadata.name); do
      local replicas=$(oc get dc $dc --template='{{ index .metadata.annotations "idling.alpha.openshift.io/previous-scale"}}' -n $project 2>/dev/null)
      if [[ $replicas =~ $_DIGIT_REGEX ]]; then
        oc scale --replicas=$replicas dc $dc -n $project
      fi
    done
  done
}

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

pushd ~ >/dev/null
START=`date +%s`

echo_header "Multi-product MSA Demo ($(date))"

case "$ARG_COMMAND" in
    delete)
        echo "Delete MSA demo ($ARG_DEMO)..."
        oc delete project  ${PRJ_COOLSTORE_PROD[0]}
        [ "$ENABLE_CI_CD" = true ] && oc delete project ${PRJ_CI[0]} ${PRJ_SERVICE_DEV[0]}
        [ "$ENABLE_TEST_ENV" = true ] && oc delete project ${PRJ_COOLSTORE_TEST[0]} ${PRJ_DEVELOPER[0]}
        echo
        echo "Delete completed successfully!"
        ;;
      
    verify)
        echo "Verifying MSA demo ($ARG_DEMO)..."
        print_info
        verify_build_and_deployments
        echo
        echo "Post-Software checks completed successfully!"
        ;;

    idle)
        echo "Idling MSA demo ($ARG_DEMO)..."
        print_info
        make_idle
        echo
        echo "Idling completed successfully!"
        ;;

    unidle)
        echo "Unidling MSA demo ($ARG_DEMO)..."
        print_info
        make_unidle
        echo
        echo "Unidling completed successfully!"
        ;;

    deploy)
        echo "Deploying MSA demo ($ARG_DEMO)..."

        if [ "$ENABLE_CI_CD" = true ] ; then
          create_cicd_projects
        else
          create_projects
        fi

        print_info
        
        deploy_nexus

        if images_exists; then
          import_images
        else
          wait_for_nexus_to_be_ready
          build_images
        fi
        
        deploy_coolstore_prod_env

        if [ "$ENABLE_CI_CD" = true ] ; then
          configure_bluegreen_in_prod
          deploy_guides
          deploy_gogs
          deploy_jenkins
          deploy_pipeline

          if [ "$ENABLE_TEST_ENV" = true ] ; then
            add_service_templates_to_projects
            deploy_coolstore_test_env
          fi

          deploy_service_dev_env

          promote_images
        fi

        if [ "$ARG_RUN_VERIFY" = true ] ; then
          echo "Waiting for deployments to finish..."
          sleep 30
          verify_build_and_deployments
        fi

        echo
        echo "Provisioning completed successfully!"
        ;;
        
    *)
        echo "Invalid command specified: '$ARG_COMMAND'"
        usage
        ;;
esac

set_default_project
popd >/dev/null

END=`date +%s`
echo "(Completed in $(( ($END - $START)/60 )) min $(( ($END - $START)%60 )) sec)"
echo 