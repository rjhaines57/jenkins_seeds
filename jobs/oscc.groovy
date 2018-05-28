pipelineJob('oscc') {
  definition {
    cps {
      sandbox()
      script("""

node {
    def volumeId
    try {
    
        stage('Setup idir')
        {
	   //	copyArtifacts filter: 'auto_triage/openmrs.csv', fingerprintArtifacts: true, projectName: 'seed-job', selector: lastSuccessful()    
        deleteDir()    
        volumeId=sh(returnStdout: true, script: '/usr/bin/docker volume create').trim() 
        }
        stage('Clone sources') {
          git url: 'https://github.com/PolySync/oscc.git'
        }
        stage('Build (C++)') {
      
            docker.image('cov-analysis:2018.03').withRun('--hostname \${BUILD_TAG} -v '+volumeId+':/opt/coverity/idirs -v coverity_tools:/opt/coverity --name wibble') { c ->
                docker.image('gcc:5.5.0').inside('--hostname \${BUILD_TAG} -v '+volumeId+':/opt/coverity/idirs  -v coverity_tools:/opt/coverity:ro') { 
					sh 'apt-get update && apt install arduino-core build-essential cmake'
					sh 'cd firmware && mkdir build && cd build && cmake .. -DKIA_SOUL=ON'
                    sh '/opt/coverity/analysis/bin/cov-configure --config /opt/coverity/idirs/coverity_config.xml --gcc'
                    sh '/opt/coverity/analysis/bin/cov-build --dir /opt/coverity/idirs/idir  --config /opt/coverity/idirs/coverity_config.xml make -j -C firmware/build'            
                }            
            }
        }
        stage('Analysis') {
            docker.image('cov-analysis:2018.03').inside('--hostname \${BUILD_TAG} --mac-address 08:00:27:ee:25:b2 -v '+volumeId+':/opt/coverity/idirs') {
                sh '/opt/coverity/analysis/bin/cov-analyze --dir /opt/coverity/idirs/idir --trial'
            }
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image('cov-analysis:2018.03').inside('--network docker_coverity --hostname \${BUILD_TAG}  --mac-address 08:00:27:ee:25:b2 -v '+volumeId+':/opt/coverity/idirs -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSPHRASE=\${COV_PASSPHRASE}') {
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password coverity --project OSCC --stream oscc'
                    sh '/opt/coverity/analysis/bin/cov-commit-defects --dir /opt/coverity/idirs/idir --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream oscc'
                }
            }
        }
    }
    finally
    {
    stage('cleanup volume') {
        // Comment out this to keep the idir volume
		sh 'docker volume rm '+volumeId
    }
    }
}
      
	  """.stripIndent())      
    }
  }
}
if (!jenkins.model.Jenkins.instance.getItemByFullName('Redis')) {
 queue('oscc')
}