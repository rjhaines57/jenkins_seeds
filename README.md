# jenkins_seeds

## Introduction

This git hub repository contains seeds for prepopulating Connect Docker Demo Environment(CDDE) with jenkins jobs. Jobs are in the form of Jenkins DSL. The purpose of this is firstly to give a good demo environment and secondly to capture the command lines/best practices/tips and tricks for building an analysing different types of codebases. As this repository is linked closely to the CDDE all of the examples initially are based on linux and building within docker. There is the possiblity of expanding this to non docker builds but it shouldn't be difficult to translate what happens in one to the other.

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

## Anatomy of a Job

All of the jobs described below are setup using scripted pipeline. Please be aware there are two different types of pipeline available for Jenkins, scripted and declaritive. While the declaritive is simpler it doesn't give much flexibility.

### The DSL Part

The DSL for most of these jobs is pretty simple:
```
pipelineJob('HelloWorld') {
  definition {
    description('This is a hello world example')
    cps {
      sandbox()
      script("""
node {
	stage('Hello World') {
		echo "Hello World BuildTag=\${BUILD_TAG}"
	}
}
     """.stripIndent())      
    }
  }
}
if (!jenkins.model.Jenkins.instance.getItemByFullName('HelloWorld') && System.getenv("AUTO_RUN")) {
  queue('HelloWorld')
}
```

### Walkthrough

You can pretty much copy the above as a template for a new job but here is a walkthrough of the different parts:

The first part sets up the job name and description:
```
pipelineJob('HelloWorld') {
  definition {
    description('This is a hello world example')
```
** NOTE: The name of the job is allowed to have spaces but if the job uses the BUILD_TAG as a unique index (for example, the name of a docker volume) then having spaces makes this a little more difficult**

The next part contains the pipeline script:
```
cps {
      sandbox()
      script("""
node {
	stage('Hello World') {
		echo "Hello World BuildTag=\${BUILD_TAG}"
	}
}
     """.stripIndent())      
    }
  }
}
```
The pipeline script is denoted \`\`\`, everything between these are a scripted pipeline job. Typically a pipeline script is stored with the repository and you will see that in the examples there are some projects that have a pipeline file, however this cannot be used in this environment as it won't include coverity. See the section below for a more indepth example 

The last part is specific to the CDDE, there are a number of different environment variables which it is possible to set when starting the docker environment. Setting the "AUTO_RUN" variable will queue the job for execution on creation. This allows a database to be populated from empty to have all the projects run with a single command.
```
if (!jenkins.model.Jenkins.instance.getItemByFullName('HelloWorld') && System.getenv("AUTO_RUN")) {
  queue('HelloWorld')
}
```

## A typical pipeline Docker Example (and all the hoops you need to jump through)

The following is a walkthough of the OpenMRS pipeline docker job. There are somethings which are a bit complicated due to some of the restrictions surround the CDDE. Please see the CDDE documentation (internal) to see more information on design decisions.

```
node {
    def volumeName='${BUILD_TAG}'
    def analysis_image="${DEFAULT_ANALYSIS_TAG}:${DEFAULT_ANALYSIS_VERSION}"
 	  def idir_base='/opt/coverity/idirs'
	  def idir=idir_base+'/idir'
	  def config=idir_base+'/coverity_config.xml'
```
The section above sets a number of variables that are used throughout the script, they are:
- volumeName - By default this is set to the build tag, this means that the volume that contains the build tools and idir is unique for this build. 
- analysis_image - This defines the analysis image and version. This set to a default in the CDDE, you can hard code these if you need to specify a particular analysis version
- idir_base, idir and config - These configure where the config and idir sit in the volume

```
try {
  stage('Copy autotriage data')
  {
  	copyArtifacts filter: 'auto_triage/openmrs.csv', fingerprintArtifacts: true, projectName: 'seed-job', selector: lastSuccessful()    
  }
```
The whole pipeline is wrapped in a try catch block. Any exceptions will be caught and dumped and the finally block will clean up the volume used. This first stage copies the auto triage data. Please see the section above on auto triage.

```
  stage('Clone sources') {
			// deleteDir()  
			git url: 'https://github.com/openmrs/openmrs-core.git'
			sh 'git checkout 9f12d2f6c1c8ebbaa51c996cb209528d2110ab03'
  }
```
The clone sources stage will download the repositiory and checkout a version. The reason for the checkout in this case is to choose a particularily bad version, removing the checkout will get the latest. Uncomment the "deleteDir()" to get the repository every time. Currently anything built with Maven will store it's repostory in the worspace directory so any invocation of deleteDir will also remove this

```
  stage('Build (Java & Javascript)') {
    docker.withRegistry('','docker_credentials') {  
	    docker.image(analysis_image).withRun('--hostname ${BUILD_TAG} -v '+volumeName+':/opt/coverity') { c ->
			  docker.image('maven:3.5.3-jdk-8').inside('--hostname ${BUILD_TAG} -e HOME=${WORKSPACE} -v '+volumeName+':/opt/coverity') { 
				  stage('Build'){
							sh '/opt/coverity/analysis/bin/cov-configure --config '+config+' --java'
							sh '/opt/coverity/analysis/bin/cov-configure --config '+config+' --javascript'                
							sh '/opt/coverity/analysis/bin/cov-build --dir '+idir+'  --config '+config+' mvn -DskipTests=true -Dmaven.compiler.forceJavacCompilerUse=true -Dlicense.skip=true clean install  '            
							sh '/opt/coverity/analysis/bin/cov-build --dir '+idir+' --config '+config+' --no-command --fs-capture-search webapp/src'  
							try {
								sh '/opt/coverity/analysis/bin/cov-import-scm --dir '+idir+' --scm git --filename-regex ${WORKSPACE}'
							}
							catch (err)  { echo "cov-import-scm returned something unsavoury, moving on:"+err}						
						}
					}            
				}
			}
   }
```
Wow! This looks fun.




```
        stage('Analysis') {
            docker.image(analysis_image).inside('--hostname ${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity ') {
                sh '/opt/coverity/analysis/bin/cov-analyze --dir '+idir+' --trial --webapp-security-trial --disable-fb'
            }
        }
```
```
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image(analysis_image).inside(' --hostname ${BUILD_TAG} --network docker_coverity --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=${COV_USER} -e COV_PASSWORD=${COV_PASSWORD}') {
                    sh 'createProjectAndStream --host ${COVERITY_HOST} --user ${COV_USER} --password ${COV_PASSWORD} --project OpenMRS --stream openmrs'
                    sh '/opt/coverity/analysis/bin/cov-commit-defects --dir '+idir+' --strip-path ${WORKSPACE} --host ${COVERITY_HOST} --port ${COVERITY_PORT} --stream openmrs'
                }
            }
        }
```
```
	//	stage('Coverity Results') {
    //        coverityResults connectInstance: 'Test Server', connectView: 'Outstanding Security Risks', projectId: 'OpenMRS', unstable:true
    //    }
		
    }
    finally
    {
    stage('cleanup volume') {
		// Delete volume
		sh 'docker volume rm '+volumeName
		}
    }
}
```






If you copy a pipeline script from an existing jenkins job or a file then please make sure that you escape any variable usage, for example:
```
\${BUILD_TAG}
```
