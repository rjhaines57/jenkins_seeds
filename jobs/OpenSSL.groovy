pipelineJob('OpenSSL') {
   description('TLS/SSL and crypto library https://www.openssl.org')
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
    def analysis_image="\${DEFAULT_ANALYSIS_TAG}:\${DEFAULT_ANALYSIS_VERSION}"
	def idir_base='/opt/coverity/idirs'
	def idir=idir_base+'/idir'
	def config=idir_base+'/coverity_config.xml'
	def backdate="\${Backdate}"
    def commit="\${Commit}"
	
    try {
        stage('Clone sources') {
			deleteDir()    
			git url: 'https://github.com/openssl/openssl.git'
			print "["+commit+"]"
			if (!commit.contains("LATEST"))
			{
			 sh 'git checkout '+commit   
			}
        }
        stage('Build (C++)') {
        docker.withRegistry('','docker_credentials') {
            docker.image(analysis_image).withRun('--hostname \${BUILD_TAG} -v '+volumeName+':/opt/coverity') { 
                    docker.image('gcc:7.3.0').inside('--hostname \${BUILD_TAG} -v '+volumeName+':/opt/coverity') { 
                        sh '/opt/coverity/analysis/bin/cov-configure --config '+config+' --gcc'
                        sh './config no-asm'
                        sh '/opt/coverity/analysis/bin/cov-build --dir '+idir+' --config '+config+' make -j 3'            
                        try {
                            sh '/opt/coverity/analysis/bin/cov-import-scm --dir '+idir+' --scm git --filename-regex \${WORKSPACE}'
                        }
                        catch (err)  { echo "cov-import-scm returned something unsavoury, moving on:"+err}
                        
                    }            
                }
            }
        }
		
        stage('Analysis') {
            docker.image(analysis_image).inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity') {
                sh '/opt/coverity/analysis/bin/cov-analyze --strip-path \${WORKSPACE} --dir '+idir+' --trial'
            }
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image(analysis_image).inside('--network docker_coverity --hostname \${BUILD_TAG}  --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity/ -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSWORD=\${COV_PASSWORD}') {
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password \${COV_PASSWORD} --project OpenSSL --stream openssl'
                    def commitCommand='/opt/coverity/analysis/bin/cov-commit-defects --dir '+idir+' --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream openssl'
                    if (!backdate.contains("NONE"))
                    {
                        commitCommand=commitCommand+" --backdate "+backdate            
                    }
                    sh commitCommand 
               
                }
            }
        }
    }
    catch (err){
        error "Caught Exception: "+err
    }
    finally
    {
        stage('Cleanup volume') {
        // Comment out this to keep the idir volume
	    	sh 'docker volume rm '+volumeName
      }
    }
}	  
      """.stripIndent())      
    }
  }
}
listView('Windows Jobs') {
    description('All jobs that run on Windows')
    filterBuildQueue()
    filterExecutors()
    jobs {
        name('OpenSSL')
        
    }
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}

if (!jenkins.model.Jenkins.instance.getItemByFullName('OpenSSL')&& System.getenv("AUTO_RUN")) {
 queue('OpenSSL')
}