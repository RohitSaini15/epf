package com.evernorth.cloudnativebuild.mocks

class Scm {
    def userRemoteConfigs = [[url:"https://git.express-scripts.com/expressScripts/fakerepo.git"]]
    def branches = [[name: 'mock-branch']]
    def credentialsId = "credId"
    def extensions = []
}
