# Use keycloak base
FROM jboss-eap-7-beta/eap70-openshift

# Maintainer details
MAINTAINER James Falkner <jfalkner@redhat.com>

ENV KEYCLOAK_VERSION 1.9.4.Final

USER jboss

# add maven mirror

RUN sed -i -e 's/<mirrors>/&\n    <mirror>\n      <id>sti-mirror<\/id>\n      <url>${env.MAVEN_MIRROR_URL}<\/url>\n      <mirrorOf>external:*<\/mirrorOf>\n    <\/mirror>/' ${HOME}/.m2/settings.xml

# install adapter

WORKDIR ${JBOSS_HOME}

RUN curl -L https://downloads.jboss.org/keycloak/$KEYCLOAK_VERSION/adapters/keycloak-oidc/keycloak-wildfly-adapter-dist-$KEYCLOAK_VERSION.tar.gz | tar zx

RUN sed 's/standalone.xml/standalone-openshift.xml/g' ${JBOSS_HOME}/bin/adapter-install-offline.cli | ${JBOSS_HOME}/bin/jboss-cli.sh

USER root

COPY projects ${JBOSS_HOME}/projects/

# perform the build

RUN mvn package -f ${JBOSS_HOME}/projects/cool-project/pom.xml -DskipTests && cp ${JBOSS_HOME}/projects/cool-project/target/coolstore-1.0.0.war ${JBOSS_HOME}/standalone/deployments && chown -R jboss ${JBOSS_HOME}/standalone/deployments 

USER jboss 

# Exposed default jboss port
EXPOSE 8080
