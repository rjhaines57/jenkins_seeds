pipelineJob('Redis') {
   description('Redis is an in-memory database that persists on disk. The data model is key-value, but many different kind of values are supported: Strings, Lists, Sets, Sorted Sets, Hashes, HyperLogLogs, Bitmaps. http://redis.io')
   logRotator {
        numToKeep(10)
        artifactNumToKeep(1)
   }
   definition {
    parameters {
        stringParam('Commit', '070d04717909e25254334f55760e972c6f8d02e3', 'Which commit do you want to build?')
		stringParam('Backdate', 'NONE', 'Do you wish to backdate this commit (Format YYYYMMDD)?')
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
			//deleteDir()    
			// src_repo:https://github.com/antirez/redis.git
			git branch: 'unstable', url: 'ssh://git@cov-git/home/git/redis.git'
			print "["+commit+"]"
			if (!commit.contains("LATEST"))
			{
			 sh 'git checkout '+commit   
			}
		  
        }
        stage('Build (C++)') {
        docker.withRegistry('','docker_credentials') {
            docker.image(analysis_image).withRun('--hostname \${BUILD_TAG} -v '+volumeName+':/opt/coverity') { 
                    docker.image('gcc:5.5.0').inside('--hostname \${BUILD_TAG} -v '+volumeName+':/opt/coverity') { 
                        sh '/opt/coverity/analysis/bin/cov-configure --config '+config+' --gcc'
                        sh '/opt/coverity/analysis/bin/cov-build --dir '+idir+' --config '+config+' make -j'            
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
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password \${COV_PASSWORD} --project Redis --stream redis'
					def commitCommand='/opt/coverity/analysis/bin/cov-commit-defects --dir '+idir+' --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream redis'
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
        echo "Caught Exception: "+err
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
