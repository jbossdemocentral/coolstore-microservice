#!/bin/bash
###############################################################
# Prvisioning script to deploy the demo on GPTE environment   #
###############################################################

USER_ID=$1
GROUP_ID=$2
OPENSHIFT_MASTER=$3

###############################################################
# CONFIGURATION                                               #
###############################################################
# VERSION=1.0
DEMO_VERSION=demo-1
PROJECT_SUFFIX=$USER_ID
GITHUB_ACCOUNT=jbossdemocentral
NEXUS_URL=http://nexus-cicd-$PROJECT_SUFFIX.cluster.local:8081/nexus/content/groups/public/
GITLAB_TEMPLATE=https://gitlab.com/gitlab-org/omnibus-gitlab/raw/8-15-stable/docker/openshift-template.json

###############################################################
# DEPLOY DEMO                                                 #
###############################################################

# Create Projects
oc new-project coolstore-test-$PROJECT_SUFFIX --display-name='Coolstore TEST' --description='Coolstore Test Environment'
oc new-project coolstore-stage-$PROJECT_SUFFIX --display-name='Coolstore STAGE' --description='Coolstore Staging Environment'
oc new-project coolstore-prod-$PROJECT_SUFFIX --display-name='Coolstore PROD' --description='Coolstore Production Environment'
oc new-project inventory-dev-$PROJECT_SUFFIX --display-name='Inventory DEV' --description='Inventory Dev Environment'
oc new-project cicd-$PROJECT_SUFFIX --display-name='CI/CD Infra' --description='CI/CD Infra Environment'

# Deploy GitLab
# TODO: set the correct hostname parameter on the template
GITLAB_URL=gitlab
oc adm policy add-scc-to-user anyuid system:serviceaccount:cicd:gitlab-ce-user
oc process \
  -f $GITLAB_TEMPLATE \
  -v GITLAB_ROOT_PASSWORD=gitlab,APPLICATION_HOSTNAME=$GITLAB_URL \
  | oc create -f - -n cicd-$PROJECT_SUFFIX

oc annotate svc/gitlab-ce \
  service.alpha.openshift.io/dependencies='[{"name":"gitlab-ce-postgresql","namespace":"","kind":"Service"},{"name":"gitlab-ce-redis","namespace":"","kind":"Service"}]'


# Deploy Nexus
oc new-app sonatype/nexus -n cicd-$PROJECT_SUFFIX
oc expose svc/nexus -n cicd-$PROJECT_SUFFIX
oc set probe dc/nexus \
	--liveness \
	--failure-threshold 3 \
	--initial-delay-seconds 30 \
	-- echo ok \
  -n cicd-$PROJECT_SUFFIX

oc set probe dc/nexus \
	--readiness \
	--failure-threshold 3 \
	--initial-delay-seconds 30 \
	--get-url=/content/groups/public \
  -n cicd-$PROJECT_SUFFIX

oc volumes dc/nexus --add \
	--name 'nexus-volume-1' \
	--type 'pvc' \
	--mount-path '/sonatype-work/' \
	--claim-name 'nexus-pv' \
	--claim-size '1G' \
	--overwrite \
  -n cicd-$PROJECT_SUFFIX

# Deploy Jenkins
oc new-app jenkins-persistent \
  -l app=jenkins
  -p JENKINS_PASSWORD=openshift \
  -n cicd-$PROJECT_SUFFIX

# Deploy Coolstore into Coolstore TEST project
oc process \
  -f https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$DEMO_VERSION/openshift/coolstore-template.yaml \
  -v GIT_REF=$DEMO_VERSION \
  -v MAVEN_MIRROR_URL=$NEXUS_URL \
  | oc create -f - -n coolstore-test-$PROJECT_SUFFIX

# Deploy Inventory Service into Inventory DEV project
oc process \
  -f https://raw.githubusercontent.com/$GITHUB_ACCOUNT/coolstore-microservice/$DEMO_VERSION/openshift/services/inventory-service.json \
  -v GIT_REF=$DEMO_VERSION \
  -v MAVEN_MIRROR_URL=$NEXUS_URL \
  | oc create -f - -n inventory-dev-$PROJECT_SUFFIX

# Set project to 'default'
oc project default
