pipelineJob('Umbraco') {
   description('The simple, flexible and friendly ASP.NET CMS used by more than 400.000 websites')
   logRotator {
        numToKeep(10)
        artifactNumToKeep(1)
   }
   definition {
    parameters {
        stringParam('Commit', 'LATEST', 'Which commit do you want to build?')
		stringParam('Backdate', 'NONE', 'Do you wish to backdate this commit (Format YYYYMMDD)?')
    }
  
    cps {
      sandbox()
      script("""

node ('master') {
    def stashName="stash_\${BUILD_TAG}"
    def analysis_image="\${DEFAULT_ANALYSIS_TAG}:\${DEFAULT_ANALYSIS_VERSION}"
	def idir='idir'
	def config='coverity_config.xml'
    def backdate="\${Backdate}"
    def commit="\${Commit}"


    try {
        stage('Clone sources') {
		    deleteDir()
			git url: 'https://github.com/umbraco/Umbraco-CMS.git'
			print "["+commit+"]"
			if (!commit.contains("LATEST"))
			{
			 sh 'git checkout '+commit   
			}
			stash stashName
        }
        node('windows')
        {
            stage('Build C#(on windows)') {
                unstash stashName
			    bat 'cd build && cov-configure --config '+config+' --cs'
			    bat 'echo %COMPUTERNAME% > hostname.txt' 
		        try {    
            	    bat 'cd build && cov-build --dir '+idir+' --config '+config+' build.bat '            
		        }
            	catch (err){
                    echo "Caught Exception: "+err
                }
            }
            stage('Analyze(on windows)') {
                bat 'cd build && cov-analyze --dir '+idir+' --trial --webapp-security-trial --disable-fb'
   	            stash stashName
            }
        }
        stage('Commit') {
           withCoverityEnv(coverityToolName: 'default', connectInstance: 'Test Server') { 
                docker.image(analysis_image).inside(' --hostname \${BUILD_TAG} --network docker_coverity --mac-address 08:00:27:ee:25:b2 -e HOME=/opt/coverity/idirs -w /opt/coverity/idirs -e COV_USER=\${COV_USER} -e COV_PASSWORD=\${COV_PASSWORD}') {
                    unstash stashName
                    sh 'createProjectAndStream --host \${COVERITY_HOST} --user \${COV_USER} --password \${COV_PASSWORD} --project "Umbraco" --stream umbraco'
                    sh '/opt/coverity/analysis/bin/cov-manage-emit --dir '+idir+' reset-host-name '
                    def commitCommand='/opt/coverity/analysis/bin/cov-commit-defects --dir build/'+idir+' --strip-path \${WORKSPACE} --host \${COVERITY_HOST} --port \${COVERITY_PORT} --stream umbraco'
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
        error("Caught Exception: "+err+" Review log to see error condition")
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
        name('NotepadPlusPlus')
        
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

if (!jenkins.model.Jenkins.instance.getItemByFullName('Umbraco')&& System.getenv("AUTO_RUN")) {
 queue('Umbraco')
}