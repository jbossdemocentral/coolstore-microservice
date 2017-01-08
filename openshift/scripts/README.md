Demo Provisioning script
======================
The provisioning scripts creates, deploys and configures all components required for
and end-to-end demo using CoolStore microservices application.

Prerequisites
============
* OpenShift environment with a quota of 5 projects and 12 GB memory
* `oc` client authenticated against the OpenShift environment

Usage
============
```
$ provision-demo.sh --user <username>

--user                 Required. OpenShift username to own the projects

--project-suffix       Optional. Adds a suffix to the project names (e.g. demo-<suffix>).
                       If not present, username will be used.

--maven-mirror-url     Optional. Uses an existing Maven repo for builds rather than deploying
                       Sonatype Nexus as part of the demo

--delete               Used in combination with --user or --project-suffix to delete the demo
                       components and clean the environment
```
