#!/usr/bin/env groovy

pipeline {

    agent any
    
    tools {
        jdk 'jdk17'
        maven 'M3.6'
    }

    triggers { pollSCM('*/5 * * * *') }

    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
    }

    stages {
    
    	stage('Build Stage') { 
    		steps {
	      		sh "mvn clean install"
	    	}
	 	}
	 	
	 	stage('SonarQube analysis') {
	    	steps {
		    	withSonarQubeEnv('AWS SONAR') {
	      			sh 'mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184:sonar'
	    		}
		    }		
  	   }
	   
	   stage('Aws authentication'){
	   		steps {
	        	sh '$(aws ecr get-login --no-include-email --region eu-central-1)'
	        }
	   }
	   
	   stage('Docker Stage for latest version') {
	   		when { branch "develop" }
	   		steps {
	   			sh "mvn dockerfile:build"
	      		sh "mvn dockerfile:build -P common"
	      		
	      	}
	   }
	   
	   stage('Push to ECR latest version') {
	   		when { branch "develop" }
	   		steps {
	      		sh "mvn dockerfile:push"
	      		sh "mvn dockerfile:push -P common"
	      	}
	   }
	   
	   stage('Docker Stage') {
	   		when { branch "master" }
	   		steps {
	   			sh "mvn dockerfile:build -P prod"
	      		sh "mvn dockerfile:build -P common"
	      		
	      	}
	   }
	   
	   stage('Push to ECR') {
	   		when { branch "master" }
	   		steps {
	      		sh "mvn dockerfile:push -P prod"
	      		sh "mvn dockerfile:push -P common"
	      	}
	   }
	   	   
	   stage('Update ECS Fargate Service CI') {
	   		when { branch "develop" }
	   		steps {
	   	 		 sh 'aws ecs update-service --service codeonce-grizzly-runtime-service --cluster codeonce-fargate-ci --region eu-central-1 --force-new-deployment'
	   		}
	   }
	   
	    stage('Update ECS Fargate Service PROD') {
	   		when { branch "master" }
	   		steps {
	   	 		 sh 'aws ecs update-service --service codeonce-grizzly-runtime-service-prod --cluster codeonce-fargate-prod --region eu-central-1 --force-new-deployment'
	   		}
	   }

	   stage('Mirroring') {
            when { branch "master" }
			steps {
                script {
					sh 'git config --global core.sshCommand "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no"'
                    if (fileExists('grizzly-api-runtime.git')) {
                        echo 'file exist'
                        sh 'rm -r grizzly-api-runtime.git'
                    }
                    withCredentials([usernamePassword(credentialsId: 'bitbucket-jenkins-user', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                        sh "git clone --mirror https://${GIT_USERNAME}:${GIT_PASSWORD}@bitbucket.org/codeonceteam/grizzly-api-runtime.git"
                    }

                    sshagent(credentials: ['github-jenkins-user']) {
                        sh 'cd grizzly-api-runtime.git'
                        sh 'rm -r .git'
                        sh 'git init'
                        if (fileExists('grizzly-api-runtime.git')) {
                            sh 'rm -r grizzly-api-runtime.git'
                        }
                        sh 'git add .'
                        sh 'git commit -m "mirroring sync"'
                        sh 'git push -f git@github.com:codeoncesoftware/grizzly-api-runtime.git master'
                    }
                }
            }
        }
    }
    post {
        always {
            junit '**/surefire-reports/*.xml'
        }
        
        failure {
             emailext (
		      subject: "FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
		      body: """<p>FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':</p>
		        <p>Check console output at "<a href="${env.BUILD_URL}">${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>"</p>""",
		      recipientProviders: [[$class: 'DevelopersRecipientProvider'],[$class: 'CulpritsRecipientProvider']]
		    )
        }
    }

}