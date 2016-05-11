# Use keycloak base
FROM jboss/keycloak:1.9.4.Final

# Maintainer details
MAINTAINER James Falkner <jfalkner@redhat.com>

COPY keycloak-config.json keycloak-setup.cli /tmp/

USER root

RUN chown jboss /tmp/keycloak-config.json /tmp/keycloak-setup.cli

USER jboss

RUN sed -i 's/KEYCLOAK_CONFIG_FILE/\/tmp\/keycloak-config.json/' /tmp/keycloak-setup.cli && \
  ${JBOSS_HOME}/bin/jboss-cli.sh --file=/tmp/keycloak-setup.cli && \
  rm -rf ${JBOSS_HOME}/standalone/configuration/standalone_xml_history/current

# Exposed default keycloak port
EXPOSE 8080
