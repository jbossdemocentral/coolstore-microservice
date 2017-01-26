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

Prerequisites
============
* OpenShift environment with a quota of 5 projects and 12 GB memory
* `oc` client authenticated against the OpenShift environment

Usage
============
```
$ provision-demo.sh --user <username>

--master               Required     OpenShift master url

--project-suffix       Required     Adds a suffix to the project names (e.g. demo-<suffix>) to make them unique.
                                    If not present, and --user is provided, username (left hand side of @ or -)
                                    will be used.

--user                 Optional     The user to own the demo projects and is used when the user provisioning
                                    the demo (e.g. 'system:admin') is different from the user owning the projects

--maven-mirror-url     Optional     If provided, this Maven repo (e.g. Nexus or Artifactory) would be used for
                                    builds. Otherwise, a Sonatype Nexus container will be created as part of
                                    demo provisioning.

--delete                            Used in combination with --user or --project-suffix to delete the demo
                                    components and clean the environment
```

Example
============
Provision on OpenShift Online/Dedicated:
```
$ provision-demo.sh --master console.preview.openshift.com --project-suffix mydemo
```

Use an existing Sonatype Nexus:
```
$ provision-demo.sh --master console.openshift.mycompany.com --project-suffix mydemo --maven-mirror-url http://nexus.repo.com/content/groups/public/
```

Provision demo as ```system:admin``` for user ```john@mycompany.com```:
```
$ oc login -u system:admin
$ provision-demo.sh --master console.openshift.mycompany.com --user john@mycompany.com
```

Delete on OpenShift Online/Dedicated:
```
$ provision-demo.sh --master console.preview.openshift.com --project-suffix mydemo --delete
```

Delete user ```john@mycompany.com``` demo as ```system:admin```:
```
$ oc login -u system:admin
$ provision-demo.sh --master console.openshift.mycompany.com --user john@mycompany.com --delete
```
