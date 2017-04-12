Demo Provisioning script
======================
The provisioning scripts creates, deploys and configures all components required for
and end-to-end demo using CoolStore microservices application. The script performs the following
steps:
* Creates the projects
* Deploys Gogs git server
* Imports this GitHub repo into Gogs
* Deploys Nexus repository manager if non specified
* Deploys Jenkins
* Deploys CoolStore microservices app in TEST project
* Deploys Inventory service in Inventory DEV project
* Configures CI/CD for Inventory service across projects
* Configures webhook on Gogs repository to trigger the pipeline

![CI/CD Demo](../../docs/images/cicd-projects.png?raw=true)
![CI/CD Demo](../../docs/images/cicd-pipeline.png?raw=true)

Prerequisites
============
* An OpenShift cluster with sufficient quota and resources
* `oc` client authenticated against the OpenShift cluster

| Demo Size | Min Memory | Min CPU | Projects |
|-----------|------------|---------|----------|
| Minimal   | 8 GB       | 2 cores | 5        |
| Full      | 16 GB      | 8 cores | 5        |


Usage
============
```
provision-demo.sh [command] [options]
provision-demo.sh --help

Example:
 provision-demo.sh --deploy --maven-mirror-url http://nexus.repo.com/content/groups/public/ --project-suffix demo

Commands:
   --deploy            Set up the demo projects and deploy demo apps
   --delete            Clean up and remove demo projects and objects
   --verify            Verify the demo is deployed correctly
   --idle              Make all demo servies idle

Options:
   --user              The admin user for the demo projects. mandatory if logged in as system:admin
   --maven-mirror-url  Use the given Maven repository for builds. If not specifid, a Nexus container is deployed in the demo
   --project-suffix    Suffix to be added to demo project names e.g. ci-SUFFIX. If empty, user will be used as suffix
   --minimal           Scale all pods except the absolute essential ones to zero to lower memory and cpu footprint
   --ephemeral         Deploy demo without persistent storage
   --run-verify        Run verify after provisioning
```

[![asciicast](https://asciinema.org/a/103399.png)](https://asciinema.org/a/103399)

Example
============
Provision a minimal demo on a local cluster without persistent storage:
```
$ oc cluster up 
$ oc create -f https://raw.githubusercontent.com/jboss-openshift/application-templates/master/jboss-image-streams.json -n openshift
$ provision-demo.sh --deploy --user developer --minimal --ephemeral
```

Provision on OpenShift Online/Dedicated and verify afterwards:
```
$ oc login https://api.preview.openshift.com --token=YOUR-TOKEN
$ provision-demo.sh --deploy --run-verify
```

Use an existing Sonatype Nexus:
```
$ provision-demo.sh --deploy --maven-mirror-url http://nexus.repo.com/content/groups/public/
```

Provision demo as ```system:admin``` for user ```john@mycompany.com```:
```
$ oc login -u system:admin
$ provision-demo.sh --deploy --user john@mycompany.com
```

Delete demo:
```
$ provision-demo.sh --delete
```

Delete demo for user ```john@mycompany.com``` while logged in as ```system:admin```:
```
$ oc login -u system:admin
$ provision-demo.sh --deploy --user john@mycompany.com
```

Verify demo is deployed correctly:
```
$ provision-demo.sh --verify
```

Make all demo services idle
```
$ provision-demo.sh --idle
```