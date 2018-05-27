pipelineJob('OpenMRS') {
  definition {
    cps {
      sandbox()
      script("""
node {
    def volumeId
    try {
    
        stage('Setup idir')
        {
		copyArtifacts filter: 'auto_triage/openmrs.csv', fingerprintArtifacts: true, projectName: 'seed-job', selector: lastSuccessful()    
       //  deleteDir()    
        volumeId=sh(returnStdout: true, script: '/usr/bin/docker volume create').trim() 
        }
        stage('Clone sources') {
            git url: 'https://github.com/openmrs/openmrs-core.git'
        }
        stage('Build (Java & Javascript)') {
      
            docker.image('cov-analysis:2018.03').withRun('--hostname $BUILD_TAG -v '+volumeId+':/opt/coverity/idirs -v coverity_tools:/opt/coverity --name wibble') { c ->
                docker.image('maven:3.5.3-jdk-8').inside('--hostname $BUILD_TAG -v '+volumeId+':/opt/coverity/idirs -v maven:/root/.m2 -v coverity_tools:/opt/coverity:ro') { 
                    sh '/opt/coverity/analysis/bin/cov-configure --config /opt/coverity/idirs/coverity_config.xml --java'
                    sh '/opt/coverity/analysis/bin/cov-configure --config /opt/coverity/idirs/coverity_config.xml --javascript'                
                    sh '/opt/coverity/analysis/bin/cov-build --dir /opt/coverity/idirs/idir  --config /opt/coverity/idirs/coverity_config.xml mvn -DskipTests=true -Dmaven.compiler.forceJavacCompilerUse=true -Dlicense.skip=true clean compile  '            
                    sh '/opt/coverity/analysis/bin/cov-build --dir /opt/coverity/idirs/idir --no-command --fs-capture-search webapp/src'            
                }            
            }
        }
        stage('Analysis') {
            docker.image('cov-analysis:2018.03').inside('--hostname $BUILD_TAG --mac-address 08:00:27:ee:25:b2 -v '+volumeId+':/opt/coverity/idirs') {
                sh '/opt/coverity/analysis/bin/cov-analyze --dir /opt/coverity/idirs/idir --trial'
            }
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image('cov-analysis:2018.03').inside('--network docker_coverity --hostname $BUILD_TAG  --mac-address 08:00:27:ee:25:b2 -v '+volumeId+':/opt/coverity/idirs -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSPHRASE=\${COV_PASSPHRASE}') {
                    sh 'env'                    
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password coverity --project OpenMRS --stream openmrs'
                    sh '/opt/coverity/analysis/bin/cov-commit-defects --dir /opt/coverity/idirs/idir --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream openmrs'
                }
            }
        }
    }
    finally
    {
    stage('cleanup volume') {
    // Delete volume
   // sh 'docker volume rm '+volumeId
    }
    }
}
      """.stripIndent())      
    }
  }
}

 queue('openMRS')

