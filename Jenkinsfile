// =================================================================
// HELPER FUNCTION: สร้างฟังก์ชันสำหรับส่ง Notification ไปยัง n8n (แนวทางเดียวกับ Express)
// =================================================================

def sendNotificationToN8n(String status, String stageName, String imageTag, String containerName, String hostPort) {
    script {
        withCredentials([string(credentialsId: 'n8n-webhook', variable: 'N8N_WEBHOOK_URL')]) {
            def payload = [
                project  : env.JOB_NAME,
                stage    : stageName,
                status   : status,
                build    : env.BUILD_NUMBER,
                image    : "${env.DOCKER_REPO}:${imageTag}",
                container: containerName,
                url      : "http://localhost:${hostPort}/",
                timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX")
            ]
            def body = groovy.json.JsonOutput.toJson(payload)
            try {
                httpRequest acceptType: 'APPLICATION_JSON',
                            contentType: 'APPLICATION_JSON',
                            httpMode: 'POST',
                            requestBody: body,
                            url: N8N_WEBHOOK_URL,
                            validResponseCodes: '200:299'
                echo "n8n webhook (${status}) sent successfully."
            } catch (err) {
                echo "Failed to send n8n webhook (${status}): ${err}"
            }
        }
    }
}

pipeline {
    agent any
    options { skipDefaultCheckout(true) }

    environment {
        DOCKER_HUB_CREDENTIALS_ID = 'dockerhub-cred'
        DOCKER_REPO               = "sakamotolv99/springboot-docker-app"

        // จำลอง DEV/PROD บน Local
        DEV_APP_NAME              = "springboot-app-dev"
        DEV_HOST_PORT             = "8081"
        PROD_APP_NAME             = "springboot-app-prod"
        PROD_HOST_PORT            = "8080"
    }

    parameters {
        choice(name: 'ACTION', choices: ['Build & Deploy', 'Rollback'], description: 'เลือก Action ที่ต้องการ')
        string(name: 'ROLLBACK_TAG', defaultValue: '', description: 'สำหรับ Rollback: ใส่ Image Tag (เช่น Git Hash หรือ dev-123)')
        choice(name: 'ROLLBACK_TARGET', choices: ['dev', 'prod'], description: 'สำหรับ Rollback: เลือก Environment')
        string(name: 'PROJECT_DIR_OVERRIDE', defaultValue: '', description: 'ทางเลือก: ระบุโฟลเดอร์โปรเจกต์ที่มี pom.xml (ปล่อยว่างให้ระบบค้นหาอัตโนมัติ)')
    }

    stages {
        
        stage('Checkout & Init') {
            when { expression { params.ACTION == 'Build & Deploy' } }
            steps {
                script {
                    checkout scm
                    // ค้นหา Directory ของโปรเจกต์
                    def found = findFiles(glob: '**/pom.xml')
                    if (found.length == 0) {
                        error 'ไม่พบ pom.xml ใน workspace'
                    }
                    // [แก้ไข] ใช้ Elvis Operator (?:) เพื่อจัดการกรณี pom.xml อยู่ที่ root
                    // ถ้า .parent เป็น null ให้ใช้ '.' (current directory) แทน
                    env.PROJECT_DIR = new File(found[0].path).parent ?: '.'
                    echo "PROJECT_DIR set to: '${env.PROJECT_DIR}'"
                }
            }
        }

        stage('Test & Package') {
            when { expression { params.ACTION == 'Build & Deploy' } }
            steps {
                // เพิ่ม script block ครอบคำสั่งทั้งหมด
                script {
                    // เข้าไปทำงานในโฟลเดอร์โปรเจกต์
                    dir(env.PROJECT_DIR) {
                        echo "Running Maven Test & Package inside Docker..."
                        // ใช้ docker.inside เพื่อจัดการสภาพแวดล้อม
                        docker.image('maven:3.9-eclipse-temurin-21').inside {
                            // รันทั้ง test และ package ในครั้งเดียว
                            sh 'mvn -B -ntp clean package'
                        }
                    }
                }
            }
            post {
                always {
                    // เก็บผลลัพธ์ Test และ Code Coverage เสมอ
                    dir(env.PROJECT_DIR) {
                        junit 'target/surefire-reports/*.xml'
                        // (Optional) หากคุณตั้งค่า JaCoCo plugin ไว้ใน pom.xml
                        publishHTML(target: [
                            allowMissing: true, reportDir: 'target/site/jacoco', reportFiles: 'index.html',
                            reportName: 'JaCoCo Coverage', keepAll: true
                        ])
                    }
                }
            }
        }

        stage('Build & Push Docker Image') {
            when { expression { params.ACTION == 'Build & Deploy' } }
            steps {
                script {
                    def imageTag = (env.BRANCH_NAME == 'main') ? sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim() : "dev-${env.BUILD_NUMBER}"
                    env.IMAGE_TAG = imageTag

                    docker.withRegistry('https://index.docker.io/v1/', DOCKER_HUB_CREDENTIALS_ID) {
                        echo "Building image: ${DOCKER_REPO}:${env.IMAGE_TAG}"
                        // Build Docker โดยใช้ context จากโฟลเดอร์โปรเจกต์ที่ถูกต้อง
                        def customImage = docker.build("${DOCKER_REPO}:${env.IMAGE_TAG}", "${env.PROJECT_DIR}")
                        
                        customImage.push()
                        if (env.BRANCH_NAME == 'main') {
                            customImage.push('latest')
                        }
                    }
                }
            }
        }

        stage('Deploy to DEV (Local Docker)') {
            when {
                expression { params.ACTION == 'Build & Deploy' }
                branch 'develop'
            }
            steps {
                script {
                    dir(env.PROJECT_DIR) {
                        sh """
                        echo "Deploying container ${DEV_APP_NAME} from latest image..."
                        docker pull ${DOCKER_REPO}:${env.IMAGE_TAG}
                        docker stop ${DEV_APP_NAME} || true
                        docker rm ${DEV_APP_NAME} || true
                        docker run -d --name ${DEV_APP_NAME} -p ${DEV_HOST_PORT}:8080 ${DOCKER_REPO}:${env.IMAGE_TAG}
                        docker ps --filter name=${DEV_APP_NAME} --format "table {{.Names}}\\t{{.Image}}\\t{{.Status}}"
                        """
                    }
                }
            }
            post {
                success {
                    sendNotificationToN8n('success', 'Deploy to DEV (Local Docker)', env.IMAGE_TAG, env.DEV_APP_NAME, env.DEV_HOST_PORT)
                }
            }
        }

        stage('Approval for Production') {
            when {
                expression { params.ACTION == 'Build & Deploy' }
                branch 'main'
            }
            steps {
                timeout(time: 1, unit: 'HOURS') {
                    input message: "Deploy image tag '${env.IMAGE_TAG}' to PRODUCTION (Local Docker on port ${PROD_HOST_PORT})?"
                }
            }
        }

        stage('Deploy to PRODUCTION (Local Docker)') {
            when {
                expression { params.ACTION == 'Build & Deploy' }
                branch 'main'
            }
            steps {
                script {
                    dir(env.PROJECT_DIR) {
                        sh """
                        echo "Deploying container ${PROD_APP_NAME} from latest image..."
                        docker pull ${DOCKER_REPO}:${env.IMAGE_TAG}
                        docker stop ${PROD_APP_NAME} || true
                        docker rm ${PROD_APP_NAME} || true
                        docker run -d --name ${PROD_APP_NAME} -p ${PROD_HOST_PORT}:8080 ${DOCKER_REPO}:${env.IMAGE_TAG}
                        docker ps --filter name=${PROD_APP_NAME} --format "table {{.Names}}\\t{{.Image}}\\t{{.Status}}"
                        """
                    }
                }
            }
            post {
                success {
                    sendNotificationToN8n('success', 'Deploy to PRODUCTION (Local Docker)', env.IMAGE_TAG, env.PROD_APP_NAME, env.PROD_HOST_PORT)
                }
            }
        }

        stage('Execute Rollback') {
            when { expression { params.ACTION == 'Rollback' } }
            steps {
                script {
                    if (params.ROLLBACK_TAG.trim().isEmpty()) {
                        error "เมื่อเลือก Rollback กรุณาระบุ 'ROLLBACK_TAG'"
                    }

                    env.TARGET_APP_NAME  = (params.ROLLBACK_TARGET == 'dev') ? env.DEV_APP_NAME  : env.PROD_APP_NAME
                    env.TARGET_HOST_PORT = (params.ROLLBACK_TARGET == 'dev') ? env.DEV_HOST_PORT : env.PROD_HOST_PORT
                    def imageToDeploy = "${DOCKER_REPO}:${params.ROLLBACK_TAG.trim()}"

                    echo "ROLLING BACK ${params.ROLLBACK_TARGET.toUpperCase()} to image: ${imageToDeploy}"

                    dir(env.PROJECT_DIR) {
                        sh """
                            docker pull ${imageToDeploy}
                            docker stop ${env.TARGET_APP_NAME} || true
                            docker rm ${env.TARGET_APP_NAME} || true
                            docker run -d --name ${env.TARGET_APP_NAME} -p ${env.TARGET_HOST_PORT}:8080 ${imageToDeploy}
                        """
                    }
                }
            }
            post {
                success {
                    sendNotificationToN8n('success', "Rollback ${params.ROLLBACK_TARGET.toUpperCase()}", params.ROLLBACK_TAG, env.TARGET_APP_NAME, env.TARGET_HOST_PORT)
                }
            }
        }
    }

    post {
        always {
            script {
                if (params.ACTION == 'Build & Deploy') {
                    echo "Cleaning up Docker images on agent..."
                    try {
                        if (env.IMAGE_TAG) {
                            sh """
                                docker image rm -f ${DOCKER_REPO}:${env.IMAGE_TAG} || true
                                docker image rm -f ${DOCKER_REPO}:latest || true
                            """
                        } else {
                            echo 'IMAGE_TAG not set, skipping image cleanup.'
                        }
                    } catch (err) {
                        echo "Could not clean up images, but continuing..."
                    }
                }
                // ส่วนของการลบ Workspace
                echo "Cleaning up workspace..."
                cleanWs()
            }
        }
        failure {
            sendNotificationToN8n('failed', 'Pipeline Failed', 'N/A', 'N/A', 'N/A')
        }
    }
}