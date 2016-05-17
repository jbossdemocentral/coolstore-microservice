# Use keycloak base
FROM jboss-eap-7-beta/eap70-openshift

# Maintainer details
MAINTAINER James Falkner <jfalkner@redhat.com>

ENV KEYCLOAK_VERSION 1.9.4.Final

USER jboss

# add maven mirror

RUN sed -i -e 's/<mirrors>/&\n    <mirror>\n      <id>sti-mirror<\/id>\n      <url>${env.MAVEN_MIRROR_URL}<\/url>\n      <mirrorOf>external:*<\/mirrorOf>\n    <\/mirror>/' ${HOME}/.m2/settings.xml

USER root

# perform the build

COPY projects ${JBOSS_HOME}/projects/

RUN mvn package -f ${JBOSS_HOME}/projects/cool-project/pom.xml -DskipTests && cp ${JBOSS_HOME}/projects/cool-project/target/coolstore-1.0.0.war ${JBOSS_HOME}/standalone/deployments && chown -R jboss ${JBOSS_HOME}/standalone/deployments 

USER jboss

# install adapter

WORKDIR ${JBOSS_HOME}

RUN curl -L https://downloads.jboss.org/keycloak/$KEYCLOAK_VERSION/adapters/keycloak-oidc/keycloak-wildfly-adapter-dist-$KEYCLOAK_VERSION.tar.gz | tar zx

# Standalone.xml modifications.
RUN sed -i -e 's/<extensions>/&\n        <extension module="org.keycloak.keycloak-adapter-subsystem"\/>/' $JBOSS_HOME/standalone/configuration/standalone-openshift.xml && \
    sed -i -e 's/<profile>/&\n        <subsystem xmlns="urn:jboss:domain:keycloak:1.1"\/>/' $JBOSS_HOME/standalone/configuration/standalone-openshift.xml && \
    sed -i -e 's/<security-domains>/&\n                <security-domain name="keycloak">\n                    <authentication>\n                        <login-module code="org.keycloak.adapters.jboss.KeycloakLoginModule" flag="required"\/>\n                    <\/authentication>\n                <\/security-domain>/' $JBOSS_HOME/standalone/configuration/standalone-openshift.xml

# Exposed default jboss port
EXPOSE 8080
