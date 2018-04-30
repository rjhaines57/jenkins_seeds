job('phpnuke') {
    scm {
        
    }
    triggers {
    }
    steps {
		copyArtifacts('seed-job') {
            includePatterns('auto_triage/openmrs.csv')
            buildSelector {
                latestSuccessful(true)
            }
		}
        shell('''#!/bin/bash

# Fixed configuration from the container. DO NOT CHANGE
COV_HOME=/opt/coverity


#configuration for this recipe
PROJECT=openmrs
STREAM=${PROJECT}
REPO=https://github.com/phpnuke/phpnuke.git
FS_CAPTURE_SEARCH="."

#Language/Compiler Selection
#Choose from: java javascript python php gcc clang ruby swift scala vb
#Use space separated list
#e.g CONFIGURE="java php javascript"
CONFIGURE="php"

. ${COV_HOME}/build_helper.sh
''')
    
	
	}
 
}

 queue('phpnuke')

