1. Create Projects
  ```
  oc new-project coolstore-test --display-name='Coolstore TEST' --description='Coolstore Test Environment'
  oc new-project coolstore-stage --display-name='Coolstore STAGE' --description='Coolstore Staging Environment'
  oc new-project coolstore-prod --display-name='Coolstore PROD' --description='Coolstore Production Environment'
  oc new-project inventory-dev --display-name='Inventory DEV' --description='Inventory Dev Environment'
  oc new-project cicd-infra --display-name='CI/CD Infra' --description='CI/CD Infrastructure Environment'
  ```
1. Deploy GitLab
  ```
  oc process -f https://gitlab.com/gitlab-org/omnibus-gitlab/raw/master/docker/openshift-template.json \
    -v GITLAB_ROOT_PASSWORD=gitlab,APPLICATION_HOSTNAME=gitlab | oc create -f - -n cicd-infra
  oc delete route gitlab-ce -n cicd-infra
  oc expose svc/gitlab-ce
  ```

1. Deploy Sonatype Nexus
  ```
  oc new-app sonatype/nexus
  oc expose svc/nexus
  oc set probe dc/nexus \
  	--liveness \
  	--failure-threshold 3 \
  	--initial-delay-seconds 30 \
  	-- echo ok

  oc set probe dc/nexus \
  	--readiness \
  	--failure-threshold 3 \
  	--initial-delay-seconds 30 \
  	--get-url=/content/groups/public

  oc volumes dc/nexus --add \
  	--name 'nexus-volume-1' \
  	--type 'pvc' \
  	--mount-path '/sonatype-work/' \
  	--claim-name 'nexus-pv' \
  	--claim-size '1G' \
  	--overwrite
  ```

1. Deploy Jenkins
  ```
  oc new-app jenkins-persistent -p JENKINS_PASSWORD=openshift
  ```

1. Deploy Coolstore
  ```
  oc process -f openshift-templates/coolstore-template.yaml -v GIT_REF=demo-1 | oc create -f - -n coolstore-test
  ```

1. Deploy Inventory in `inventory-dev` project
  ```
  oc process -f openshift-templates/services/inventory-service.json -v GIT_REF=demo-1 | oc create -f - -n inventory-dev
  ```
