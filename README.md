# jenkins_seeds

## Introduction

This git hub job contains seed jobs for prepopulating Connect Docker Demo Environment with jobs (CDDE). Jobs are in the form of Jenkins DSL. The purpose of this is firstly to give a good demo environment and secondly to capture the command lines/best practices/tips and tricks for building an analysing different types of codebases. As this repository is linked closely to the CDDE all of the examples initially are based on linux and building within docker. There is the possiblity of expanding this to non docker builds but it shouldn't be difficult to translate what happens in one to the other.

## Layout

Layout:
- jobs - Contains groovy files that describes the jobs
- auto_triage - Contains triage files to automatically triage issues in connect as part of job running
- config - Contains files required during the build of jobs, for example MISRA or HIS metrics

### Jobs

In this directory there are a number of .groovy files. These are Jenksins DSL files that will be read by a predefined job on the Jenkins server. If the job does **not** exist then it will be created. Updates to the .groovy file will not be reflected in Jenkins until the old job is removed. 

For more information on the Jenkins DSL then see [here](https://github.com/jenkinsci/job-dsl-plugin) and for syntax see [here](https://jenkinsci.github.io/job-dsl-plugin/)

### Auto Triage

In this directory are csv files for the autotriage script. Auto Triage allows defects to be triaged automatically, this allows a realistic demo environmemt where some issues have already been marked as bugs, FP or intentional. Files here are added as Artifacts of the seed-job (which processes the DSL) and can be brought into the build using the copy artifacts plugin.

Producing the CSV file can be done by creating a view in Connect and selecting the fields you want to triage and exporting the CSV file. The required fields that should be in this view are: "Merge Key" and "Merge Extra". If you are taking these results from a different connect to then please make sure that you do not have any custom attributes. The auto triage should work on a vanilla version.

### config

Use this area to store any other config required for the job that is in addition to the repository. Examples would include configuration files, custom patches, etc. If the data is quite large then consider requesting it be added to the data container associated with the CDDE. If you wish to add a codebase not in a repository or a non-linux code base intermediate directory (See the notepad++ example) then this should also go into the data container as it is better optimised for a large amount of data and will only be used if the appropriate job is run.


