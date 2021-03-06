pipelineJob('Jenkins') {
	description("Jenkins automation server https://jenkins.io/")

   logRotator {
        numToKeep(10)
        artifactNumToKeep(1)
   }
   
   definition {
    parameters {
        stringParam('Commit', 'LATEST', 'Which commit do you want to build?')
		stringParam('Backdate', 'NONE', 'Do you wish to backdate this commit (Format YYYYMMDD)?')
		stringParam('AnalysisVersion', '${DEFAULT_ANALYSIS_VERSION}', 'Set the analysis version or use "DEFAULT" to use the default set in DEFAULT_ANALYSIS_VERSION')
    }
    cps {
      sandbox()
      script("""

node {
    // Set volume Name to \${BUILD_TAG} for each build gives new volume and therefore clean
    // environment. Use \${JOB_NAME} for incremental builds
    def volumeName='\${BUILD_TAG}'
    def analysis_image="\${DEFAULT_ANALYSIS_TAG}:"+AnalysisVersion
	def idir_base='/opt/coverity/idirs'
	def idir=idir_base+'/idir'
	def config=idir_base+'/coverity_config.xml'
    def backdate="\${Backdate}"
    def commit="\${Commit}"

    try {
        stage('Clone sources') {
		 //  deleteDir()    
            git url: 'https://github.com/jenkinsci/jenkins.git'
			print "["+commit+"]"
			if (!commit.contains("LATEST"))
			{
			 sh 'git checkout '+commit   
			}
		}
        stage('Build (Java & Javascript)') {
            docker.withRegistry('','docker_credentials') {  
				docker.image(analysis_image).withRun('--hostname \${BUILD_TAG} -v '+volumeName+':/opt/coverity') { c ->
					docker.image('maven:3.6.0-jdk-8').inside('--hostname \${BUILD_TAG} -e HOME=\${WORKSPACE} -v '+volumeName+':/opt/coverity') { 
						stage('Build'){
							sh '/opt/coverity/analysis/bin/cov-configure --config '+config+' --java'
							sh '/opt/coverity/analysis/bin/cov-configure --config '+config+' --javascript'   
							sh '/opt/coverity/analysis/bin/cov-build --dir '+idir+'  --config '+config+' mvn -DskipTests=true -Dmaven.compiler.forceJavacCompilerUse=true -Dlicense.skip=true clean install  '            
							sh '/opt/coverity/analysis/bin/cov-build --dir '+idir+' --config '+config+' --no-command --fs-capture-search war/src'  
							try {
								sh '/opt/coverity/analysis/bin/cov-import-scm --dir '+idir+' --scm git --filename-regex \${WORKSPACE}'
							}
							catch (err)  { echo "cov-import-scm returned something unsavoury, moving on:"+err}						
						}
					}            
				}
			}
		}
        
        stage('Analysis') {
            docker.image(analysis_image).inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity') {
                sh '/opt/coverity/analysis/bin/cov-analyze --dir '+idir+' --trial --webapp-security-trial --disable-fb'
            }
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image(analysis_image).inside(' --hostname \${BUILD_TAG} --network docker_coverity --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSWORD=\${COV_PASSWORD}') {
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password \${COV_PASSWORD} --project Jenkins --stream jenkins'
					def commitCommand='/opt/coverity/analysis/bin/cov-commit-defects --dir '+idir+' --strip-path \${WORKSPACE} --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream jenkins'
                    if (!backdate.contains("NONE"))
                    {
                        commitCommand=commitCommand+" --backdate "+backdate            
                    }
                    sh commitCommand 
                }
            }
        }
	//	stage('Coverity Results') {
    //         coverityResults connectInstance: 'Test Server', connectView: 'Outstanding Security Risks', projectId: 'Jenkins', unstable:true
    //    }
		
    }
    catch (err){
        error "Caught Exception: "+err
    }
    finally
    {
        stage('cleanup volume') {
            // Delete volume
            sh 'docker volume rm '+volumeName
        }
    }
}
      	  
	  """.stripIndent())      
    }
  }
}
if (!jenkins.model.Jenkins.instance.getItemByFullName('Jenkins') && System.getenv("AUTO_RUN")) {
 queue('Jenkins')
}
