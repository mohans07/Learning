import static org.common.Utilities.*
import static org.common.Constants.*

def call(String APPLICATION_NAME) {
	pipeline {
		agent any
		stages {
			stage('Build/Prompt Pull Request') {
				steps {
					script{
						env.APPLICATION_NAME ="$APPLICATION_NAME"
						if(!env.CHANGE_ID){
							timeout(time: 30, unit: 'MINUTES') {
								script{
									def OBJECT = input message: "Please enter your deployment details", ok: "Continue", parameters: [
										string(name: 'CHANGE_ID', defaultValue: '', description: 'Enter the pull request number'),
										string(name: 'BUILD_ID', defaultValue: '', description: 'Enter the build id')
									]
									if(OBJECT.CHANGE_ID != '' && OBJECT.BUILD_ID != ''){
										env.BUILD_ID ="BLD-${OBJECT.BUILD_ID}"
										env.CHANGE_ID = "${OBJECT.CHANGE_ID}-$BUILD_ID"
										sendMessage this,"Build Skipped - ${env.JOB_NAME}-${BUILD_ID} (<${env.BUILD_URL}|Open>)"
									} else {
										sendMessage this,"Please give valid input! - ${env.JOB_NAME}-${BUILD_ID} (<${env.BUILD_URL}|Open>)"
									}
								}
							}	
						} else {
							env.BUILD_ID = "BLD-${env.BUILD_ID}"
							env.CHANGE_ID = "${env.CHANGE_ID}-$BUILD_ID"
							sendMessage this,"Build Started - ${env.JOB_NAME}-${BUILD_ID} (<${env.BUILD_URL}|Open>)"
							sh '''
								echo "docker build --force-rm -t ${REGISTRY}/${APPLICATION_NAME}:PR-${CHANGE_ID} ."
								set +x
								docker build --force-rm --build-arg SSH_PRIVATE_KEY="$(cat ~/.ssh/id_rsa)" -t ${REGISTRY}/${APPLICATION_NAME}:PR-${CHANGE_ID} .
								set -x
								docker push ${REGISTRY}/${APPLICATION_NAME}:PR-${CHANGE_ID}
								docker rmi ${REGISTRY}/${APPLICATION_NAME}:PR-${CHANGE_ID}'''
							sendMessage this,"Build Finished - ${env.JOB_NAME}-${BUILD_ID} (<${env.BUILD_URL}|Open>)"
						}
					}
				}
			}
			stage('Select Env to Deploy'){
				when {
					beforeInput true
					changeRequest()
				}
				steps{
					script {
						try {
							timeout(time: 15, unit: 'MINUTES') {
								script{
									def environment = input message: "select an Environment to deploy?", ok: "Select", parameters: [
									choice(name: 'environment', choices: ['Nothing','Testing','Staging','PreProd','Production','Kubernetes-Staging'], description: 'Please select the environment to deploy')
									]
									env.environment = environment
									echo "Environment selected: $environment"
								}
							}
						}
						catch(Exception err){
							env.environment = "Nothing"
						}
					}
				}
			}
			stage('Deploy') {
				when {
					beforeInput true
					changeRequest()
				}
				parallel {
					stage('Testing') {
						when {
							equals expected: 'Testing', actual: environment
						}
						steps {
							script{
								sendMessage this, "Deploying to ${environment} - Branch: ${BRANCH_NAME}"
							}
							sshagent(credentials: ['testing']){
								lock('testing-server'){
									script{
										deploymentHandler(test_nodes)
									}
								}
							}
						}
					}	
					stage('Staging') {
						when {
							equals expected: 'Staging', actual: environment
						}
						steps {
							script{
								sendMessage this, "Deploying to ${environment} - Branch: ${BRANCH_NAME}"
							}
							sshagent(credentials: ['staging']){
								lock('staging-server'){
									script{
										if ( "$APPLICATION_NAME" == "ui"){
											deploymentHandler(stage_ui_nodes)
										}else{
											deploymentHandler(stage_api_nodes)
										}
									}
								}
							}
						}
					}	
					stage('Pre-Prod') {
						when {
							equals expected: 'PreProd', actual: environment
						}
						steps {
							script{
								sendMessage this, "Deploying to ${environment} - Branch: ${BRANCH_NAME}"
							}
							sshagent(credentials: ['pre-prod']){
								lock('pre-prod-server'){
									script{
										deploymentHandler(pre_production_api_nodes)
									}
								}
							}
						}
					}
					stage('Production') {
						when {
							equals expected: 'Production', actual: environment
						}
						steps {
							script{
								sendMessage this,"Deploying to ${environment} - Branch: ${BRANCH_NAME}"
							}
							sshagent(credentials: ['prod']){
								lock('production-server'){
									script{
										if ( "$APPLICATION_NAME" == "ui"){
											deploymentHandler(production_ui_nodes)
										} else if ( "$APPLICATION_NAME" == "corpweb") {
											deploymentHandler(corp_web_nodes)
										}
										else{
											deploymentHandler(production_api_nodes)
										}
									}
								}
							}
						}
					}
					stage('Kubernetes-Staging') {
						when {
							equals expected: 'Kubernetes-Staging', actual: environment
						}
						steps {
							script{
								sendMessage this,"Deploying to ${environment} - Branch: ${BRANCH_NAME}"
							}
							sshagent(credentials: ['staging']){
								lock('kubernetes-staging-server'){
									script{
										deploymentHandler(kubernetes_stage_api_nodes)
									}
								}
							}
						}
					}
					// stage('Kubernetes-Production') {
					// 	when {
					// 		equals expected: 'Kubernetes-Production', actual: environment
					// 	}
					// 	steps {
					// 		script{
					// 			sendMessage this,"Deploying to ${environment} - Branch: ${BRANCH_NAME}"
					// 		}
					// 		sshagent(credentials: ['prod']){
					// 			lock('kubernetes-production-server'){
					// 				script{
					// 					echo "TBD"
					// 				}
					// 			}
					// 		}
					// 	}
					// }
					stage('Do not do') {
						when {
							equals expected: 'Nothing', actual: environment
						}
						steps {
							sh '''echo 'No env chosen in given timeout. Nothing to do.' '''
						}
					}
				}
			}
		}
	}
}
