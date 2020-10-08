pipelineJob('oscc') {
  description('Open Source Car Control ')
   logRotator {
        numToKeep(10)
        artifactNumToKeep(1)
   }
   definition {
    parameters {
        stringParam('Commit', 'c08ae9982941842679b6b21847211c55d6db500c', 'Which commit do you want to build?')
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
          //deleteDir()    //Uncomment this to make a clean build every time
	  // src_repo:https://github.com/PolySync/oscc.git
          git url: 'ssh://git@cov-git/home/git/oscc.git'
		  print "["+commit+"]"
		  if (!commit.contains("LATEST")) {
			 sh 'git checkout '+commit   
			}
        }
        stage('Copy autotriage data')
        {
			copyArtifacts filter: 'config/HIS_all_MISRA_c2012.config', fingerprintArtifacts: false, projectName: 'seed-job', selector: lastSuccessful()    
        }		
        stage('Create helper image')
        {
            sh 'echo "FROM gcc:5.5.0" > Dockerfile'
            sh 'echo "RUN apt-get update && apt-get install -y arduino-core build-essential cmake" >> Dockerfile'
            docker.build("oscc-build:latest")
        }
        stage('Build (C++)') {
            docker.withRegistry('','docker_credentials') {  		
				docker.image(analysis_image).withRun('--hostname \${BUILD_TAG}  -v '+volumeName+':/opt/coverity') { c ->
					docker.image('oscc-build:latest').inside('--hostname \${BUILD_TAG}   -v '+volumeName+':/opt/coverity') { 
						sh 'cd firmware && rm -rf build && mkdir build && cd build && cmake .. -DKIA_SOUL=ON'
						sh '/opt/coverity/analysis/bin/cov-configure --config '+config+' --compiler avr-gcc --comptype gcc --template'
						sh '/opt/coverity/analysis/bin/cov-build --dir '+idir+' --emit-complementary-info   --config '+config+' make -j -C firmware/build'    
						try {
							sh '/opt/coverity/analysis/bin/cov-import-scm --dir '+idir+' --scm git --filename-regex \${WORKSPACE}'
						}
						catch (err)  { echo "cov-import-scm returned something unsavoury, moving on:"+err}												
					}            
				}
			}
        }
        
        parallel security : {stage('Analysis Security') {
            docker.image(analysis_image).inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity') {
                sh '/opt/coverity/analysis/bin/cov-analyze --dir '+idir+' -all --output-tag security'
            }
        }},
        misra:{stage('Analysis Misra') {
            docker.image(analysis_image).inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity') {
                sh '/opt/coverity/analysis/bin/cov-analyze --dir '+idir+' --coding-standard-config config/HIS_all_MISRA_c2012.config --disable-default --output-tag misra'
            }
        }}
        
        stage('Commit Security') {
                withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                    docker.image(analysis_image).inside('--network docker_coverity --hostname \${BUILD_TAG}  --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity -e HOME=/opt/coverity/idirs -w  /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSWORD=\${COV_PASSWORD}') {
                        sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password \${COV_PASSWORD} --project OSCC --stream oscc_security'
                        def commitCommand='/opt/coverity/analysis/bin/cov-commit-defects --dir '+idir+' --strip-path \${WORKSPACE} --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream oscc_security --output-tag security'
                        if (!backdate.contains("NONE"))
                        {
                            commitCommand=commitCommand+" --backdate "+backdate            
                        }
                        sh commitCommand 

                    }
                }
            }
        
            stage('Commit Misra') {
                withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                    docker.image(analysis_image).inside('--network docker_coverity --hostname \${BUILD_TAG}  --mac-address 08:00:27:ee:25:b2 -v '+volumeName+':/opt/coverity -e HOME=/opt/coverity/idirs -w  /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSWORD=\${COV_PASSWORD}') {
                        sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password \${COV_PASSWORD} --project OSCC --stream oscc_misra'
                        def commitCommand='/opt/coverity/analysis/bin/cov-commit-defects --dir '+idir+' --strip-path \${WORKSPACE} --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream oscc_misra --output-tag misra'
                        if (!backdate.contains("NONE"))
                        {
                            commitCommand=commitCommand+" --backdate "+backdate            
                        }
                        sh commitCommand 

                        sh 'cp '+idir+'/outputmisra/HIS_MISRA_c2012_report.html \$WORKSPACE'
                        archiveArtifacts artifacts: 'HIS_MISRA_c2012_report.html'
                    }
                }
            }
        
		stage('Publish HTML')
		{
			publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: '.', reportFiles: 'HIS_MISRA_c2012_report.html', reportName: 'HIS Report', reportTitles: ''])
		
		}
    stage('Coverity Results') {
         coverityResults connectInstance: 'Test Server', connectView: 'OSCC_CI_View', projectId: 'OSCC', unstable:true
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
if (!jenkins.model.Jenkins.instance.getItemByFullName('oscc') && System.getenv("AUTO_RUN") ) {
 queue('oscc')
}
