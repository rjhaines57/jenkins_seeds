job('openmrs') {
    scm {
        
    }
    triggers {
    }
    steps {
		copyArtifacts('upstream') {
            includePatterns('auto_triage')
            buildSelector {
                latestSuccessful(true)
            }
        shell('''#!/bin/bash

# Fixed configuration from the container. DO NOT CHANGE
COV_HOME=/opt/coverity


#configuration for this recipe
PROJECT=openmrs
STREAM=${PROJECT}
REPO=https://github.com/openmrs/openmrs-core.git
WAR=webapp/target/openmrs.war
COMMIT=9f12d2f6c1c8ebbaa51c996cb209528d2110ab03
BUILD_COMMAND="mvn install -Dmaven.test.skip=true"
FS_CAPTURE_SEARCH="webapp/src"

#Language/Compiler Selection
#Choose from: java javascript python php gcc clang ruby swift scala vb
#Use space separated list
#e.g CONFIGURE="java php javascript"
CONFIGURE="java javascript"

. ${COV_HOME}/build_helper.sh
''')
    
	
	}
 
}

 queue('openmrs')

