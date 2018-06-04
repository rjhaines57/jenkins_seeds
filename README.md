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
- volumeName - By default this is set to the build tag, this means that the volume that contains the build tools and idir is unique for this build.  If you want to have a persistent idir then you will need to use something like ${JOB_NAME} instead
- analysis_image - This defines the analysis image and version. This set to a default in the CDDE, you can hard code these if you need to specify a particular analysis version
- idir_base, idir and config - These configure where the config and idir sit in the volume

```
try {
  stage('Copy autotriage data')
  {
  	copyArtifacts filter: 'auto_triage/openmrs.csv', fingerprintArtifacts: true, projectName: 'seed-job', selector: lastSuccessful()    
  }
```
The whole pipeline is wrapped in a try catch block. Any exceptions will be caught and dumped and the finally block will clean up the volume used. The 'stage' keyword defines a new build stage, when you see the result of the run it will show the different stages, it may be possible in some cases to run stages in parallel but for Coverity it will be linear. In this case the first stage copies the auto triage data from the last successfull seed-job. See the auto triage section above for more details.

```
  stage('Clone sources') {
			// deleteDir()  
			git url: 'https://github.com/openmrs/openmrs-core.git'
			sh 'git checkout 9f12d2f6c1c8ebbaa51c996cb209528d2110ab03'
  }
```
The clone sources stage will download the repositiory and checkout a version. The reason for the checkout in this case is to choose a particularily bad version, removing the checkout will get the latest. Uncomment the "deleteDir()" to get the repository every time. Currently anything built with Maven will store it's .m2 repository in the workspace directory so any invocation of deleteDir will also remove this.

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

Wow! This looks fun. The 'sh' commands in the middle should be self explanatory for a Coverity build with Maven so lets take the first part line by line:

```
    docker.withRegistry('','docker_credentials') {  
``` 

This logins into the Docker Hub. For security/legal reasons some of the docker images are stored in private repositories. The login will allow the image to be pulled in the next line. **Note** I've not wrapped every stage with this as once it is pulled then it should be available locally and the login is not required. Why do I have to do this when I had to login already to run the CDDE? That was from outside the docker environment, now we are inside it we need to do it again. It just happens we are running these docker commands from inside a docker environment but they could easily be running from a normal jenkins instance.

```
	docker.image(analysis_image).withRun('--hostname ${BUILD_TAG} -v '+volumeName+':/opt/coverity') {
```

This starts an analysis container based on the analysis_image. The actual build will take place in a different container but we need this container to get access to the coverity tools. The '-v' section specifies the use of a volume mounted at directory /opt/coverity. When the volume is created, all the coverity tools will be copied/made visible/wished into being by pixies from the analysis container into the volume. This is the mechanism by which the tools are made available in the build container. The 'withRun' directive simply starts the container

```
      docker.image('maven:3.5.3-jdk-8').inside('--hostname ${BUILD_TAG} -e HOME=${WORKSPACE} -v '+volumeName+':/opt/coverity') { 
```
This starts a maven container based on the image 'maven:3.5.3-jdk-8'. Most of the jobs in the repository use a tool container for doing the build. This allows flexiblity to use any container necessary to build the code base without having to deal with the mess of adding/maintaining the tools in a generic build/analysis container. It's a thing of beauty. Note the same "-v" option now mounts our previously created volume into this container. What you will not see in the job but what happens automatically underneath is that the docker pipeline plugin will mount the workspace as a volume and set all the usual environment variables so to all intents at this point you are setting in the job workspace ready with the right tools including coverity, ready to do some work. Finally for this stage:
```
	try {
		sh '/opt/coverity/analysis/bin/cov-import-scm --dir '+idir+' --scm git --filename-regex ${WORKSPACE}'
	}
	catch (err)  { echo "cov-import-scm returned something unsavoury, moving on:"+err}						
```
For some reason this sometimes gives a failed return code, it's been wrapped in a try catch block to stop it failing the pipeline. Onto the next stage:
```
        stage('Analysis') {
            docker.image(analysis_image).inside('--hostname ${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity ') {
                sh '/opt/coverity/analysis/bin/cov-analyze --dir '+idir+' --trial --webapp-security-trial --disable-fb'
            }
        }
```
This uses the same analysis image however it will start a new container. Note that the commands are now being run in this container rather than a tool container. The volume is mounted again, this may appear unusual as the tools are actually in this container however the idir is in the volume. In the future this might change to move the idir back into the workspace.

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
The commit stage creates a project and stream and then commits to coverity. The createProjectAndStream script is supplied by Kevin Matthews and is part of his suite of scripts used for coverity deployments. Note the --network option here, this is the first time that any of our analysis containers or tools containers have need to access the internal network, that is they may have communicated with the outside world but not with other docker containers in the CDDE. The name of the network is hard coded in the docker-compose.yml file, should really be dynamic but this works for now :( 

```
        stage('Coverity Results') {
        coverityResults connectInstance: 'Test Server', connectView: 'Outstanding Security Risks', projectId: 'OpenMRS', unstable:true
        }
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
The Coverity Results stage will retrieve results from the server and produce a pretty graph. It depends on a coverity view to get it's information. Note: This is typically disable initially as creating views in connect is not available via the API so a sensible default for this is not available, also views are not created until the first login so if the user has never logged in before, which is the case for this environment when initially installed, this will fail. 

The final stage will clean up the docker volume. Remove this sh command to keep the build log and intermediate directory volume. Note that a subsequent run will create a new idir as the volume is named after the build tag which has the build number in it.




