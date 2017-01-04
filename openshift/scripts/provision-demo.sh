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
PROJECT_SUFFIX=${ARG_PROJECT_SUFFIX:-`echo $ARG_USERNAME | sed -e 's/-.*//g'`}
GITHUB_ACCOUNT=${GITHUB_ACCOUNT:-jbossdemocentral}
GITHUB_REF=${GITHUB_REF:-demo-1-gpte}
MAVEN_MIRROR_URL=${ARG_MAVEN_MIRROR_URL:-http://nexus.cicd-$PROJECT_SUFFIX.svc.cluster.local:8081/content/groups/public}

################################################################################
# FUNCTIONS                                                                    #
################################################################################

function delete_projects() {
  oc delete project coolstore-test-$PROJECT_SUFFIX coolstore-stage-$PROJECT_SUFFIX coolstore-prod-$PROJECT_SUFFIX inventory-dev-$PROJECT_SUFFIX cicd-$PROJECT_SUFFIX
}

# Create Infra Project
function create_infra_project() {
  echo_header "Creating infra project..."
  oc new-project cicd-$PROJECT_SUFFIX --display-name='CI/CD Infra' --description='CI/CD Infra Environment'
}

# Create Application Project
function create_app_projects() {
  echo_header "Creating application projects..."
  oc new-project coolstore-test-$PROJECT_SUFFIX --display-name='CoolStore TEST' --description='CoolStore Test Environment'
  oc new-project coolstore-stage-$PROJECT_SUFFIX --display-name='CoolStore STAGE' --description='CoolStore Staging Environment'
  oc new-project coolstore-prod-$PROJECT_SUFFIX --display-name='CoolStore PROD' --description='CoolStore Production Environment'
  oc new-project inventory-dev-$PROJECT_SUFFIX --display-name='Inventory DEV' --description='Inventory Dev Environment'

  # join project networks
  if [ "$(oc whoami)" == 'system:admin' ] ; then
    oc adm pod-network join-projects --to=cicd-$PROJECT_SUFFIX coolstore-test-$PROJECT_SUFFIX coolstore-stage-$PROJECT_SUFFIX coolstore-prod-$PROJECT_SUFFIX inventory-dev-$PROJECT_SUFFIX
  fi

  # add Inventory Service template
  oc create -f https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/services/inventory-service.json -n inventory-dev-$PROJECT_SUFFIX
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
  if [ -z "$ARG_MAVEN_MIRROR_URL" ] ; then # no maven mirror specified
    echo_header "Deploying Sonatype Nexus repository manager..."
    oc process -f https://raw.githubusercontent.com/OpenShiftDemos/nexus/master/nexus2-persistent-template.yaml | oc create -f - -n cicd-$PROJECT_SUFFIX
  else
    echo_header "Using existng Maven mirror: $ARG_MAVEN_MIRROR_URL"
  fi
}

# Wait till Nexus is ready
function wait_for_nexus_to_be_ready() {
  if [ -z "$ARG_MAVEN_MIRROR_URL" ] ; then # no maven mirror specified
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
  fi
}

# Deploy Jenkins
function deploy_jenkins() {
  echo_header "Deploying Jenkins..."
  oc new-app jenkins-persistent -l app=jenkins -p JENKINS_PASSWORD=openshift -n cicd-$PROJECT_SUFFIX
}

# Deploy Coolstore into Coolstore TEST project
function deploy_coolstore_test_env() {
  echo_header "Deploying CoolStore app into coolstore-test-$PROJECT_SUFFIX project..."
  oc process -f https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/coolstore-persistent-template.yaml -v GIT_REF=$GITHUB_REF -v MAVEN_MIRROR_URL=$MAVEN_MIRROR_URL | oc create -f - -n coolstore-test-$PROJECT_SUFFIX
}

# Deploy Inventory Service into Inventory DEV project
function deploy_inventory_service() {
  echo_header "Deploying Inventory service into inventory-dev-$PROJECT_SUFFIX project..."
  oc process -f https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$GITHUB_REF/openshift/services/inventory-service.json -v GIT_REF=$GITHUB_REF -v MAVEN_MIRROR_URL=$MAVEN_MIRROR_URL | oc create -f - -n inventory-dev-$PROJECT_SUFFIX
}

function set_permissions() {
  oc adm policy add-role-to-user admin $ARG_USERNAME -n coolstore-test-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin $ARG_USERNAME -n coolstore-stage-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin $ARG_USERNAME -n coolstore-prod-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin $ARG_USERNAME -n inventory-dev-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin $ARG_USERNAME -n cicd-$PROJECT_SUFFIX

  oc adm policy add-role-to-user admin system:serviceaccounts:cicd-$PROJECT_SUFFIX -n coolstore-test-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin system:serviceaccounts:cicd-$PROJECT_SUFFIX -n coolstore-stage-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin system:serviceaccounts:cicd-$PROJECT_SUFFIX -n coolstore-prod-$PROJECT_SUFFIX
  oc adm policy add-role-to-user admin system:serviceaccounts:cicd-$PROJECT_SUFFIX -n inventory-dev-$PROJECT_SUFFIX
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

create_infra_project
extract_and_set_domain
deploy_nexus
deploy_gogs
deploy_jenkins

create_app_projects
wait_for_nexus_to_be_ready
deploy_coolstore_test_env
# deploy_inventory_service

set_default_project
set_permissions
