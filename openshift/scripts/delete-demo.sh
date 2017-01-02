#!/bin/bash
###############################################################
# Housekeeping script to remove demo from GPTE environment    #
###############################################################

USER_ID=${1:-demo}  # e.g. ssadeghi-redhat.com
GUID=$2 # unique identifier
OPENSHIFT_MASTER=$3

PROJECT_SUFFIX=$(echo $USER_ID | sed 's/-redhat.com//g')

oc delete project \
  coolstore-test-$PROJECT_SUFFIX \
  coolstore-stage-$PROJECT_SUFFIX \
  coolstore-prod-$PROJECT_SUFFIX \
  inventory-dev-$PROJECT_SUFFIX \
  cicd-$PROJECT_SUFFIX
