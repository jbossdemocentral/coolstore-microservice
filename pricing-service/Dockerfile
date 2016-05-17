# Use keycloak base
FROM jboss-eap-7-beta/eap70-openshift

# Maintainer details
MAINTAINER James Falkner <jfalkner@redhat.com>

ENV KEYCLOAK_VERSION 1.9.4.Final

USER jboss

# perform the build

COPY pom.xml src ${JBOSS_HOME}/cool-project

RUN mvn package -f ${JBOSS_HOME}/cool-project/pom.xml && cp ${JBOSS_HOME}/cool-project/target/coolstore-1.0.0.war ${JBOSS_HOME}/standalone/deployments

# install adapter

WORKDIR /opt/jboss/wildfly

RUN curl -L https://downloads.jboss.org/keycloak/$KEYCLOAK_VERSION/adapters/keycloak-oidc/keycloak-wildfly-adapter-dist-$KEYCLOAK_VERSION.tar.gz | tar zx

WORKDIR /opt/jboss

# Standalone.xml modifications.
RUN sed -i -e 's/<extensions>/&\n        <extension module="org.keycloak.keycloak-adapter-subsystem"\/>/' $JBOSS_HOME/standalone/configuration/standalone-openshift.xml && \
    sed -i -e 's/<profile>/&\n        <subsystem xmlns="urn:jboss:domain:keycloak:1.1"\/>/' $JBOSS_HOME/standalone/configuration/standalone-openshift.xml && \
    sed -i -e 's/<security-domains>/&\n                <security-domain name="keycloak">\n                    <authentication>\n                        <login-module code="org.keycloak.adapters.jboss.KeycloakLoginModule" flag="required"\/>\n                    <\/authentication>\n                <\/security-domain>/' $JBOSS_HOME/standalone/configuration/standalone-openshift.xml

# Exposed default jboss port
EXPOSE 8080
