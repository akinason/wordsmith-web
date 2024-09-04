pipeline {
    agent any 

    environment {
        IMAGE_REGISTRY = "235364608836.dkr.ecr.eu-central-1.amazonaws.com"
        AWS_REGION = "eu-central-1"
        SNS_TOPIC_ARN = "arn:aws:sns:eu-central-1:235364608836:jenkins-notification"
    }

    stages{
        stage("Init"){
            steps{
                script{
                    def dockerHome = tool "docker"
                    env.PATH = "${dockerHome}/bin:${env.PATH}"
                }
            }
        }

        stage("Git") {
            steps {
                script{
                    git branch: 'main', credentialsId: 'github-token', url: 'https://github.com/akinason/wordsmith-web.git'
                }
            }
        }


        // stage("Sonar Scan") {
        //     tools {
        //         maven "maven-3.9"
        //     }
        //     steps{
        //         withSonarQubeEnv("sonar"){
        //             withCredentials([string(credentialsId: 'jenkins-sonarqube-secret', variable: 'SONAR_TOKEN')]){
        //                 sh "mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.7.0.1746:sonar -Dsonar.projectKey=wordsmith-api -Dsonar.token=${env.SONAR_TOKEN}"
        //             }
        //         }
        //     }
        // }

        // stage("Quality Gates") {
        //     steps{
        //         timeout(time: 3, unit: "MINUTES"){
        //             waitForQualityGate abortPipeline: true
        //         }
        //     }
        // }

        stage("Build Docker Image") {
            steps{
                script{
                    def tag = getDockerTag()
                    sh "docker build -t ${env.IMAGE_REGISTRY}/wordsmith-web:${tag} ."
                    sh "docker images"
                }
            }
        }

        stage("Image Scan") {
            steps {
                script{
                    def tag = getDockerTag()
                    sh "trivy image --exit-code 1 --severity HIGH,CRITICAL ${env.IMAGE_REGISTRY}/wordsmith-web:${tag}"
                }
            }
        }

        stage("Push to ECR") {
            steps {
                script{
                    def tag = getDockerTag()
                    withAWS([credentials: 'aws-creds', region: "${env.AWS_REGION}"]) {
                        sh "aws ecr get-login-password --region ${env.AWS_REGION} | docker login --username AWS --password-stdin ${env.IMAGE_REGISTRY}"
                        sh "docker push ${env.IMAGE_REGISTRY}/wordsmith-web:${tag}"
                    }
                }
            }
        }

        stage("Deploy") {
            when {branch 'main'}
            steps {
                script{
                    def tag = getDockerTag()
                    build job: 'wordsmith-web-cd', parameters: [string(name: 'IMAGE_TAG', value: "${tag}")], wait: false
                }
            }
        }
    }

    post{
        always {
            script{
                cleanWs()
            }
        }
        failure {
            script{
                def subject = "${env.JOB_NAME} Build FAILED: ${env.BUILD_NUMBER}"
                def message = """
                Status: FAILED
                View: ${env.BUILD_URL}
                """
                withAWS([credentials: "aws-creds", region: "${env.AWS_REGION}"]) {
                    sh "aws sns publish --topic-arn ${env.SNS_TOPIC_ARN} --message '${message}' --subject '${subject}'"
                }
            }
        }
        success {
            script{
                sh "docker rmi -f \$(docker images -q)"
                def buildStatus = currentBuild.result ?: "SUCCESS"
                def subject = "${env.JOB_NAME} Build Success: ${env.BUILD_NUMBER}"
                def message = """
                Status: ${buildStatus}
                Branch: ${env.BRANCH_NAME}
                View: ${env.BUILD_URL}console
                """
                withAWS([credentials: "aws-creds", region: "${env.AWS_REGION}"]) {
                    sh "aws sns publish --topic-arn ${env.SNS_TOPIC_ARN} --message '${message}' --subject '${subject}'"
                }
            }
        }
    }
}


def getDockerTag() {
    // develop => 1.0-SNAPSHOT.22-rc , main => 1.0.0.22  , feature => 1.0.0.22-feature-something
    def version = "1.1.0";
    def branch = "${env.BRANCH_NAME}";
    def buildNumber = "${env.BUILD_NUMBER}";

    def tag = "";

    if (branch == "main") {
        tag = "${version}.${buildNumber}";
    } else if(branch == "develop") {
        tag = "${version}.${buildNumber}-rc";
    } else {
        branch = branch.replace("/", "-").replace("\\", "-");
        tag = "${version}.${buildNumber}-${branch}";
    }

    return tag;
}
