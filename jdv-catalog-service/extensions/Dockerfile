FROM registry.access.redhat.com/jboss-datavirt-6/datavirt63-openshift

LABEL Name="jdv-catalog-service" \
      Version="latest"

COPY files /extensions/
#COPY modules /extensions/modules/
COPY install.sh /extensions/
COPY install.properties /extensions/

