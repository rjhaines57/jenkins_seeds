# jenkins_seeds

This git hub job contains seed jobs for prepopulating Connect with jobs. Jobs are in the form of Jenkins DSL (https://github.com/jenkinsci/job-dsl-plugin/wiki)

Layout:
  jobs - Contains groovy files that describes the jobs
  auto_triage - Contains triage files to automatically triage issues in connect as part of job running
  config - Contains files required during the build of jobs, for example MISRA or HIS metrics
