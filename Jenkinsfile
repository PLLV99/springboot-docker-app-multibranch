// =================================================================
// CI/CD Pipeline: Spring Boot + Docker (Multibranch)
//
// Flow:
//   develop branch → Test → Build Image → Deploy to DEV (automatic)
//   main branch    → Test → Build Image → Manual Approval → Deploy to PROD
//   Rollback       → "Build with Parameters", select Rollback + specify an old tag
//
// Project conventions:
//   - pom.xml, Dockerfile, and Jenkinsfile always live at the repo root
//   - Image tags: main uses the short git hash / develop uses dev-{BUILD_NUMBER}
// =================================================================


// =================================================================
// HELPER: Send a notification to an n8n webhook
//
// Declaring functions outside the pipeline {} block is a standard
// pattern in Declarative Pipeline for reusable logic.
// (If your org has many projects, functions like this belong in a
//  Shared Library instead of being copy-pasted into every Jenkinsfile.)
// =================================================================
def sendNotificationToN8n(String status, String stageName, String imageTag, String containerName, String hostPort) {
    // Pull the webhook URL from Jenkins Credentials.
    // Never hardcode URLs or secrets in the Jenkinsfile.
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
        // try/catch: a failed notification must never fail the pipeline.
        // Notifications are a side effect, not the actual work.
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

// =================================================================
// HELPER: Deploy a container and wait for its health check
//
// Deployment logic lives in one place (DRY) — shared by DEV, PROD,
// and Rollback. Change the deploy behavior once, it applies everywhere.
//
// Syntax note: inside a Groovy """...""" block, a bare $ is
// interpolated by Groovy BEFORE the script reaches the shell.
// Shell-side variables (e.g. $READY, $(seq)) must be escaped as \$.
// =================================================================
def deployAndVerify(String containerName, String hostPort, String image) {
    sh """
        echo "Deploying container ${containerName} from image ${image}..."
        docker pull ${image}
        # || true = don't fail if the container doesn't exist yet (first deploy)
        docker stop ${containerName} || true
        docker rm ${containerName} || true
        docker run -d --name ${containerName} -p ${hostPort}:8080 ${image}

        # --- Smoke test: poll the health endpoint until the app is ready ---
        # IMPORTANT: this Jenkins runs INSIDE a Docker container, so
        # "localhost" here is the Jenkins container itself — NOT the host
        # where port ${hostPort} is published. We therefore resolve the app
        # container's own IP on the Docker bridge network and hit its
        # internal port 8080 directly (container-to-container traffic).
        # Spring Boot takes several seconds to boot; poll every 2s,
        # up to 30 times = 60-second timeout.
        echo "Waiting for app to be ready..."
        APP_IP=\$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' ${containerName})
        echo "Health-checking http://\$APP_IP:8080/actuator/health"
        READY=false
        for i in \$(seq 1 30); do
            if curl -sf http://\$APP_IP:8080/actuator/health > /dev/null; then
                READY=true
                break
            fi
            sleep 2
        done

        if [ "\$READY" = "true" ]; then
            echo "App is UP!"
            curl -s http://\$APP_IP:8080/actuator/health
        else
            # App didn't come up within 60s → dump container logs for debugging,
            # then fail the pipeline (exit 1), which triggers post { failure }.
            echo "App failed to start within 60s"
            docker logs ${containerName}
            exit 1
        fi
    """
}


pipeline {
    // agent any = run on any available node.
    // (Real-world setups usually pin a label, e.g. agent { label 'docker' },
    //  to guarantee the job lands on a machine with the right tooling.)
    agent any

    // Disable the implicit checkout — we check out manually in the first
    // stage so it only happens for Build & Deploy.
    // (Rollback doesn't need source code; it redeploys an existing image.)
    options { skipDefaultCheckout(true) }

    environment {
        // Credential IDs as configured in Manage Jenkins → Credentials
        DOCKER_HUB_CREDENTIALS_ID = 'dockerhub-cred'
        DOCKER_REPO               = "sakamotolv99/springboot-docker-app"

        // DEV/PROD simulated with Docker on a single machine, separated by port.
        // *** In real production, DEV/PROD are separate machines/clusters. ***
        DEV_APP_NAME              = "springboot-app-dev"
        DEV_HOST_PORT             = "8081"
        PROD_APP_NAME             = "springboot-app-prod"
        PROD_HOST_PORT            = "8080"
    }

    parameters {
        choice(name: 'ACTION', choices: ['Build & Deploy', 'Rollback'], description: 'Select the action to perform')
        string(name: 'ROLLBACK_TAG', defaultValue: '', description: 'For Rollback: the image tag to roll back to (e.g. a git hash or dev-123)')
        choice(name: 'ROLLBACK_TARGET', choices: ['dev', 'prod'], description: 'For Rollback: target environment')
        // Note: on the very first build of a new job, Jenkins doesn't know
        // these parameters yet (it must parse the Jenkinsfile once first).
        // This is expected multibranch behavior, not a bug.
    }

    stages {

        stage('Checkout & Init') {
            // when {} = condition controlling whether this stage runs.
            // Rollback skips checkout: it uses an image already on the registry.
            when { expression { params.ACTION == 'Build & Deploy' } }
            steps {
                script {
                    checkout scm
                    // Convention over Configuration:
                    // We don't write code to SEARCH for pom.xml — we declare the
                    // rule that it must be at the repo root, then verify it
                    // fail-fast. If someone breaks the convention, the build dies
                    // in the first 10 seconds with a clear message instead of
                    // failing mysteriously mid-build.
                    // (fileExists is a native pipeline step. Never use
                    //  new java.io.File(): the Groovy sandbox blocks it, and it
                    //  would resolve paths on the controller, not the agent.)
                    if (!fileExists('pom.xml')) {
                        error 'pom.xml not found at repo root — this project requires pom.xml at the root by convention'
                    }
                }
            }
        }

        stage('Test & Package') {
            when { expression { params.ACTION == 'Build & Deploy' } }
            steps {
                script {
                    echo "Running Maven Test & Package inside Docker..."
                    // Run Maven inside a container instead of installing
                    // Maven/JDK on the Jenkins host.
                    // Benefits: switching Java versions = changing an image tag,
                    // and every build gets an identical, clean environment.
                    docker.image('maven:3.9-eclipse-temurin-21').inside {
                        // -B   = batch mode (no interactive prompts)
                        // -ntp = no transfer progress (keeps logs clean)
                        sh 'mvn -B -ntp clean package'
                    }
                }
            }
            post {
                // post { always } = runs whether the stage passed or failed,
                // so test results are visible even when tests break
                // (which is exactly when you need them most).
                always {
                    // allowEmptyResults = don't fail the post block if the build
                    // died before producing any test reports.
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                    publishHTML(target: [
                        allowMissing: true, reportDir: 'target/site/jacoco', reportFiles: 'index.html',
                        reportName: 'JaCoCo Coverage', keepAll: true
                    ])
                }
            }
        }

        stage('Build & Push Docker Image') {
            when { expression { params.ACTION == 'Build & Deploy' } }
            steps {
                script {
                    // Tagging rules:
                    //   main    → short git hash (e.g. 64a7005) — always traceable
                    //             back to the exact commit
                    //   develop → dev-{build number} (e.g. dev-42)
                    // *** Never deploy by :latest alone — you can't roll back
                    //     because you don't know what "latest" was yesterday.
                    //     This is exactly why specific tags matter. ***
                    def imageTag = (env.BRANCH_NAME == 'main') ? sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim() : "dev-${env.BUILD_NUMBER}"
                    env.IMAGE_TAG = imageTag

                    docker.withRegistry('https://index.docker.io/v1/', DOCKER_HUB_CREDENTIALS_ID) {
                        echo "Building image: ${DOCKER_REPO}:${env.IMAGE_TAG}"
                        // "." = build context is the workspace root (per convention)
                        def customImage = docker.build("${DOCKER_REPO}:${env.IMAGE_TAG}", ".")

                        customImage.push()
                        // main also pushes :latest as a convenience alias
                        // (deploys always use the specific tag, never :latest)
                        if (env.BRANCH_NAME == 'main') {
                            customImage.push('latest')
                        }
                    }
                }
            }
        }

        stage('Deploy to DEV (Local Docker)') {
            when {
                // Two conditions inside when {} are ANDed — both must be true.
                expression { params.ACTION == 'Build & Deploy' }
                branch 'develop'
            }
            steps {
                script {
                    deployAndVerify(env.DEV_APP_NAME, env.DEV_HOST_PORT, "${DOCKER_REPO}:${env.IMAGE_TAG}")
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
                // Manual approval gate — the pipeline pauses until a human
                // confirms before anything touches production.
                // timeout(1 HOUR) = if nobody approves, abort instead of
                // holding an executor hostage forever.
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
                    deployAndVerify(env.PROD_APP_NAME, env.PROD_HOST_PORT, "${DOCKER_REPO}:${env.IMAGE_TAG}")
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
                    // Validate inputs before doing anything — fail fast with a
                    // clear message.
                    if (params.ROLLBACK_TAG.trim().isEmpty()) {
                        error "ROLLBACK_TAG is required when ACTION is Rollback"
                    }

                    env.TARGET_APP_NAME  = (params.ROLLBACK_TARGET == 'dev') ? env.DEV_APP_NAME  : env.PROD_APP_NAME
                    env.TARGET_HOST_PORT = (params.ROLLBACK_TARGET == 'dev') ? env.DEV_HOST_PORT : env.PROD_HOST_PORT
                    def imageToDeploy = "${DOCKER_REPO}:${params.ROLLBACK_TAG.trim()}"

                    echo "ROLLING BACK ${params.ROLLBACK_TARGET.toUpperCase()} to image: ${imageToDeploy}"

                    // Rollback = deploying an old image by its specific tag,
                    // using the exact same function as a normal deploy — which
                    // also gives us the health check for free (after a rollback
                    // you MUST prove the old version actually came up).
                    // This only works because every image was tagged specifically
                    // at build time.
                    deployAndVerify(env.TARGET_APP_NAME, env.TARGET_HOST_PORT, imageToDeploy)
                }
            }
            post {
                success {
                    sendNotificationToN8n('success', "Rollback ${params.ROLLBACK_TARGET.toUpperCase()}", params.ROLLBACK_TAG, env.TARGET_APP_NAME, env.TARGET_HOST_PORT)
                }
            }
        }
    }

    // Pipeline-level post = runs after all stages, regardless of the result.
    post {
        always {
            script {
                if (params.ACTION == 'Build & Deploy') {
                    // Remove built images from the Jenkins host to prevent the
                    // disk from filling up. (The real copies live on Docker Hub,
                    // and running containers keep their own image reference.)
                    echo "Cleaning up Docker images on agent..."
                    try {
                        if (env.IMAGE_TAG) {
                            sh """
                                docker image rm -f ${DOCKER_REPO}:${env.IMAGE_TAG} || true
                                docker image rm -f ${DOCKER_REPO}:latest || true
                            """
                        } else {
                            // No IMAGE_TAG = the pipeline failed before the build stage.
                            echo 'IMAGE_TAG not set, skipping image cleanup.'
                        }
                    } catch (err) {
                        echo "Could not clean up images, but continuing..."
                    }
                }
                echo "Cleaning up workspace..."
                cleanWs()
            }
        }
        failure {
            // Notify on every pipeline failure, whichever stage it died in.
            sendNotificationToN8n('failed', 'Pipeline Failed', 'N/A', 'N/A', 'N/A')
        }
    }
}