pipelineJob('Redis') {
  definition {
    cps {
      sandbox()
      script("""

node {
    def volumeId
    def analysis_image="clittlej/sig-emea-ses:analysis-2018.03"
    try {
    
        stage('Setup idir')
        {
	   //	copyArtifacts filter: 'auto_triage/openmrs.csv', fingerprintArtifacts: true, projectName: 'seed-job', selector: lastSuccessful()    
        deleteDir()    
        volumeId=sh(returnStdout: true, script: '/usr/bin/docker volume create').trim() 
        }
        stage('Clone sources') {
          git branch: 'unstable', url: 'https://github.com/antirez/redis.git'
		  'sh git checkout 070d04717909e25254334f55760e972c6f8d02e3'
        }
        stage('Build (C++)') {
      
            docker.image(analysis_image).withRun('--hostname \${BUILD_TAG} -v \${BUILD_TAG}:/opt/coverity') { 
                docker.image('gcc:5.5.0').inside('--hostname \${BUILD_TAG} -v \${BUILD_TAG}:/opt/coverity') { 
                    sh '/opt/coverity/analysis/bin/cov-configure --config /opt/coverity/idirs/coverity_config.xml --gcc'
                    sh '/opt/coverity/analysis/bin/cov-build --dir /opt/coverity/idirs/idir --config /opt/coverity/idirs/coverity_config.xml make -j'            
                    sh '/opt/coverity/analysis/bin/cov-import-scm --dir /opt/coverity/idirs/idir --scm git --filename-regex \${WORKSPACE}'
                }            
            }
        }
		
        stage('Analysis') {
            docker.image(analysis_image).inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v \${BUILD_TAG}:/opt/coverity') {
                sh '/opt/coverity/analysis/bin/cov-analyze --strip-path \${WORKSPACE} --dir /opt/coverity/idirs/idir --trial'
            }
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image(analysis_image).inside('--network docker_coverity --hostname \${BUILD_TAG}  --mac-address 08:00:27:ee:25:b2 -v \${BUILD_TAG}:/opt/coverity/ -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSPHRASE=\${COV_PASSPHRASE}') {
                    sh 'python /opt/coverity/cov_tools/coverity_jenkins_tools/createProjectAndStream.py --host \${COVERITY_HOST} --user \${COV_USER} --password coverity --project Redis --stream redis'
                    sh '/opt/coverity/analysis/bin/cov-commit-defects --dir /opt/coverity/idirs/idir --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream redis'
                }
            }
        }
    }
    catch (err){
      echo "Found error"
    }
    finally
    {
    stage('cleanup volume') {
        // Comment out this to keep the idir volume
		sh 'docker volume rm '+volumeId+' \${BUILD_TAG}'
    }
    }
}
      
	  
      
	  """.stripIndent())      
    }
  }
}
if (!jenkins.model.Jenkins.instance.getItemByFullName('Redis')) {
 queue('Redis')
}
