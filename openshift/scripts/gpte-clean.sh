#!/bin/bash
###############################################################
# Housekeeping script to remove demo from GPTE environment    #
###############################################################

USER_ID=$1
GROUP_ID=$2
OPENSHIFT_MASTER=$3

oc delete project \
  coolstore-test-$USER_ID \
  coolstore-stage-$USER_ID \
  coolstore-prod-$USER_ID \
  inventory-dev-$USER_ID \
  cicd-$USER_ID
