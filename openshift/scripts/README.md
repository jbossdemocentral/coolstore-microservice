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

[![asciicast](https://asciinema.org/a/103399.png)](https://asciinema.org/a/103399)

Prerequisites
============
* OpenShift environment with a quota of 5 projects and 12 GB memory
* `oc` client authenticated against the OpenShift environment

Usage
============
```
provision-demo.sh [options]
provision-demo.sh --help

Example:
 provision-demo.sh --maven-mirror-url http://nexus.repo.com/content/groups/public/ --project-suffix demo

Options:
   --user              The admin user for the demo projects. mandatory if logged in as system:admin
   --maven-mirror-url  Use the given Maven repository for builds. If not specifid, a Nexus container is deployed in the demo
   --project-suffix    Suffix to be added to demo project names e.g. ci-SUFFIX. If empty, user will be used as suffix
   --delete            Clean up and remove demo projects and objects
   --minimal           Scale all pods except the absolute essential ones to zero to lower memory and cpu footprint
   --help              Dispaly help
   --ephemeral         Deploy demo without persistent storage```
```

Example
============
Provision a minimal demo on local (e.g. using `oc cluster`) single-node cluster without persistent storage:
```
$ oc login 127.0.0.1:8443
$ provision-demo.sh --minimal --ephemeral
```

Provision on OpenShift Online/Dedicated:
```
$ oc login https://api.preview.openshift.com --token=YOUR-TOKEN
$ provision-demo.sh 
```

Use an existing Sonatype Nexus:
```
$ provision-demo.sh --maven-mirror-url http://nexus.repo.com/content/groups/public/
```

Provision demo as ```system:admin``` for user ```john@mycompany.com```:
```
$ oc login -u system:admin
$ provision-demo.sh --user john@mycompany.com
```

Delete demo:
```
$ provision-demo.sh --delete
```

Delete demo for user ```john@mycompany.com``` while logged in as ```system:admin```:
```
$ oc login -u system:admin
$ provision-demo.sh --user john@mycompany.com --delete
```
