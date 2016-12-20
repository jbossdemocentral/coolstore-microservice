#!/bin/bash
###############################################################
# Prvisioning script to deploy the demo on GPTE environment   #
###############################################################

# VERSION=1.0
VERSION=demo-1
GROUP_ID=$1
USER_ID=$2
OPENSHIFT_MASTER=$3

# Create Projects
oc new-project coolstore-test-$USER_ID --display-name='Coolstore TEST' --description='Coolstore Test Environment'
oc new-project coolstore-stage-$USER_ID --display-name='Coolstore STAGE' --description='Coolstore Staging Environment'
oc new-project coolstore-prod-$USER_ID --display-name='Coolstore PROD' --description='Coolstore Production Environment'
oc new-project inventory-dev-$USER_ID --display-name='Inventory DEV' --description='Inventory Dev Environment'
oc new-project cicd-$USER_ID --display-name='CI/CD Infra' --description='CI/CD Infra Environment'


# Deploy GitLab
# TODO: set the hostname parameter on the template
oc adm policy add-scc-to-user anyuid system:serviceaccount:cicd:gitlab-ce-user
oc process \
  -f https://gitlab.com/gitlab-org/omnibus-gitlab/raw/master/docker/openshift-template.json \
  -v GITLAB_ROOT_PASSWORD=gitlab,APPLICATION_HOSTNAME=gitlab \
  | oc create -f - -n cicd-$USER_ID

oc delete route gitlab-ce -n cicd-$USER_ID
oc expose svc/gitlab-ce -n cicd-$USER_ID

# Deploy Nexus
oc new-app sonatype/nexus -n cicd-$USER_ID
oc expose svc/nexus -n cicd-$USER_ID
oc set probe dc/nexus \
	--liveness \
	--failure-threshold 3 \
	--initial-delay-seconds 30 \
	-- echo ok
  -n cicd-$USER_ID

oc set probe dc/nexus \
	--readiness \
	--failure-threshold 3 \
	--initial-delay-seconds 30 \
	--get-url=/content/groups/public
  -n cicd-$USER_ID

oc volumes dc/nexus --add \
	--name 'nexus-volume-1' \
	--type 'pvc' \
	--mount-path '/sonatype-work/' \
	--claim-name 'nexus-pv' \
	--claim-size '1G' \
	--overwrite
  -n cicd-$USER_ID

# Deploy Jenkins
oc new-app jenkins-persistent -p JENKINS_PASSWORD=openshift -n cicd-$USER_ID

# Deploy Coolstore into Coolstore TEST project
oc process \
  -f https://raw.githubusercontent.com/siamaksade/coolstore-microservice/$VERSION/openshift-templates/coolstore-template.yaml \
  -v GIT_REF=$VERSION \
  | oc create -f - -n coolstore-test-$USER_ID

# Deploy Inventory Service into Inventory DEV project
oc process \
  -f https://raw.githubusercontent.com/siamaksade/coolstore-microservice/$VERSION/openshift-templates/services/inventory-service.json \
  -v GIT_REF=demo-1 \
  | oc create -f - -n inventory-dev-$USER_ID
