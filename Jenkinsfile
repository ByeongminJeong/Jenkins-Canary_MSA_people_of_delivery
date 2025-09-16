pipeline {
    agent any
    
    triggers {
        pollSCM('H/2 * * * *')
    }
    
    environment {
        GIT_COMMIT_SHORT = sh(
            script: 'git rev-parse --short=7 HEAD',
            returnStdout: true
        ).trim()
    }

    stages {
        stage('Generate Tags') {
            steps {
                script {
                    def currentBranch = env.GIT_BRANCH ?: env.BRANCH_NAME ?: 'unknown'
                    def branchNameClean = currentBranch.replaceAll('^origin/', '').replaceAll('^refs/heads/', '')

                    def imageTag
                    def deploymentStrategy

                    if (branchNameClean in ['main', 'master']) {
                        imageTag = "v${env.GIT_COMMIT_SHORT}"
                        deploymentStrategy = "production"
                    } else if (branchNameClean in ['cloud', 'cloud-deploy']) {
                        imageTag = "canary-${env.GIT_COMMIT_SHORT}"
                        deploymentStrategy = "canary"
                    } else if (branchNameClean in ['dev', 'develop', 'development']) {
                        imageTag = "dev-${env.GIT_COMMIT_SHORT}"
                        deploymentStrategy = "development"
                    } else {
                        imageTag = "${branchNameClean}-${env.GIT_COMMIT_SHORT}"
                        deploymentStrategy = "feature"
                    }

                    env.BRANCH_NAME_CLEAN = branchNameClean
                    env.IMAGE_TAG = imageTag
                    env.DEPLOYMENT_STRATEGY = deploymentStrategy

                    echo "브랜치: ${branchNameClean}, 전략: ${deploymentStrategy}, 태그: ${imageTag}"
                }
            }
        }

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Load Configuration') {
            steps {
                configFileProvider([configFile(fileId: 'all-services', variable: 'CONFIG_FILE')]) {
                    sh '''
                        . $CONFIG_FILE
                        /usr/local/bin/aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID
                        /usr/local/bin/aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY
                        /usr/local/bin/aws configure set default.region $AWS_REGION
                    '''
                }
            }
        }

        stage('Clean Environment') {
            steps {
                sh '''
                    echo "환경 정리 중..."
                    /usr/local/bin/kubectl delete deployment auth-deployment user-deployment -n app --ignore-not-found=true
                    /usr/local/bin/kubectl delete rollout auth-service-rollout user-service-rollout -n app --ignore-not-found=true
                    sleep 10
                '''
            }
        }

        stage('Create ConfigMaps and Secrets') {
            steps {
                configFileProvider([configFile(fileId: 'all-services', variable: 'CONFIG_FILE')]) {
                    sh '''
                        . $CONFIG_FILE

                        /usr/local/bin/kubectl create namespace app --dry-run=client -o yaml | /usr/local/bin/kubectl apply -f -

                        mkdir -p /tmp/k8s-config

                        # ConfigMap 생성
                        cat > /tmp/k8s-config/app-config.properties << EOF
SPRING_PROFILES_ACTIVE=production
DEPLOYMENT_STRATEGY=${DEPLOYMENT_STRATEGY}
SPRING_DATASOURCE_URL=${DB_URL}
SPRING_REDIS_HOST=${REDIS_HOST}
SPRING_REDIS_PORT=${REDIS_PORT}
REDIS_HOST=${REDIS_HOST}
REDIS_PORT=${REDIS_PORT}
SPRING_MAIL_USERNAME=${MAIL_USERNAME}
GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
DOMAIN=${DOMAIN}
AUTH_SERVICE_PORT=${AUTH_SERVICE_PORT}
USER_SERVICE_PORT=${USER_SERVICE_PORT}
MODULE_COMMON_SERVICE_PORT=${MODULE_COMMON_SERVICE_PORT}
KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS}
EOF

                        # Secret 생성
                        cat > /tmp/k8s-config/app-secrets.properties << EOF
SPRING_DATASOURCE_USERNAME=${DB_USERNAME}
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}
JWT_REFRESH_SECRET=${JWT_REFRESH_SECRET}
PASSPORT_SECRET=${PASSPORT_SECRET}
SPRING_MAIL_PASSWORD=${MAIL_PASSWORD}
GOOGLE_CLIENT_SECRET_ID=${GOOGLE_CLIENT_SECRET_ID}
AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
EOF

                        /usr/local/bin/kubectl create configmap app-config -n app --from-env-file=/tmp/k8s-config/app-config.properties --dry-run=client -o yaml | /usr/local/bin/kubectl apply -f -
                        /usr/local/bin/kubectl create secret generic app-secrets -n app --from-env-file=/tmp/k8s-config/app-secrets.properties --dry-run=client -o yaml | /usr/local/bin/kubectl apply -f -

                        rm -rf /tmp/k8s-config
                    '''
                }
            }
        }

        stage('Start Docker Daemon') {
            steps {
                sh '''
                    pkill -f dockerd || true
                    sleep 5
                    dockerd --host=unix:///var/run/docker.sock --host=tcp://0.0.0.0:2376 &
                    sleep 30

                    for i in {1..10}; do
                        if docker version >/dev/null 2>&1; then
                            echo "Docker 연결 성공!"
                            break
                        else
                            echo "Docker 연결 시도 $i/10..."
                            sleep 5
                        fi
                    done
                '''
            }
        }

        stage('ECR Login') {
            steps {
                configFileProvider([configFile(fileId: 'all-services', variable: 'CONFIG_FILE')]) {
                    sh '''
                        . $CONFIG_FILE
                        ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
                        /usr/local/bin/aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_REGISTRY
                    '''
                }
            }
        }

        stage('Build Services') {
            steps {
                configFileProvider([configFile(fileId: 'all-services', variable: 'CONFIG_FILE')]) {
                    sh '''
                        . $CONFIG_FILE
                        ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                        chmod +x ./gradlew
                        ./gradlew clean --no-daemon

                        # Auth Service 빌드
                        ./gradlew :auth-service:build -x test --no-daemon
                        docker build -f ./auth-service/Dockerfile -t ${ECR_REGISTRY}/${ECR_PREFIX}/auth-service:${IMAGE_TAG} .
                        docker push ${ECR_REGISTRY}/${ECR_PREFIX}/auth-service:${IMAGE_TAG}

                        # User Service 빌드
                        ./gradlew :user-service:build -x test --no-daemon
                        docker build -f ./user-service/Dockerfile -t ${ECR_REGISTRY}/${ECR_PREFIX}/user-service:${IMAGE_TAG} .
                        docker push ${ECR_REGISTRY}/${ECR_PREFIX}/user-service:${IMAGE_TAG}

                        # 카나리 배포용 태그 추가
                        if [ "${DEPLOYMENT_STRATEGY}" = "canary" ]; then
                            docker tag ${ECR_REGISTRY}/${ECR_PREFIX}/auth-service:${IMAGE_TAG} ${ECR_REGISTRY}/${ECR_PREFIX}/auth-service:canary
                            docker tag ${ECR_REGISTRY}/${ECR_PREFIX}/user-service:${IMAGE_TAG} ${ECR_REGISTRY}/${ECR_PREFIX}/user-service:canary
                            docker push ${ECR_REGISTRY}/${ECR_PREFIX}/auth-service:canary
                            docker push ${ECR_REGISTRY}/${ECR_PREFIX}/user-service:canary
                        fi
                    '''
                }
            }
        }

        stage('Deploy Services') {
            steps {
                configFileProvider([configFile(fileId: 'all-services', variable: 'CONFIG_FILE')]) {
                    sh '''
                        . $CONFIG_FILE
                        ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                        # 환경변수 치환 함수
                        substitute_vars() {
                            local input_file="$1"
                            local output_file="$2"
                            local ACTUAL_IMAGE_TAG="${IMAGE_TAG}"

                            if [ "${DEPLOYMENT_STRATEGY}" = "canary" ]; then
                                ACTUAL_IMAGE_TAG="canary"
                            fi

                            sed -e "s|\\${ECR_REGISTRY}|$ECR_REGISTRY|g" \\
                                -e "s|\\${ECR_PREFIX}|$ECR_PREFIX|g" \\
                                -e "s|\\${IMAGE_TAG}|$ACTUAL_IMAGE_TAG|g" \\
                                "$input_file" > "$output_file"
                        }

                        case "${DEPLOYMENT_STRATEGY}" in
                            canary)
                                echo "=== 카나리 배포 (ALB 최적화) ==="
                                cd aws/canary-deployment/services

                                # 현재 디렉토리 및 파일 구조 확인
                                echo "현재 작업 디렉토리: $(pwd)"
                                echo "파일 구조 확인:"
                                ls -la ../../eks-app/services/ || echo "../../eks-app/services/ 경로 확인 실패"
                                find ../../.. -name "*service*.yaml" -path "*/eks-app/services/*" || echo "eks-app 서비스 파일 검색 실패"

                                # 1. Services 먼저 배포 (ALB 대상그룹 생성)
                                echo "1. Services 배포..."

                                # 절대 경로로 수정하여 확실히 찾을 수 있도록 함
                                if [ -f "../../eks-app/services/auth-service.yaml" ]; then
                                    echo "eks-app의 auth-service.yaml 사용"
                                    /usr/local/bin/kubectl apply -f ../../eks-app/services/auth-service.yaml -n app
                                    echo "Auth Service (stable/canary) 배포 완료"
                                else
                                    echo "../../eks-app/services/auth-service.yaml 파일을 찾을 수 없습니다."
                                    echo "현재 위치에서 auth-service 파일 검색:"
                                    find . -name "*auth*service*.yaml" -type f
                                    echo "루트에서 auth-service 파일 검색:"
                                    find ../../.. -name "auth-service.yaml" -type f

                                    # 대체 경로 시도
                                    if [ -f "auth-service/auth-service-services.yaml" ]; then
                                        echo "대체 파일 사용: auth-service/auth-service-services.yaml"
                                        /usr/local/bin/kubectl apply -f auth-service/auth-service-services.yaml -n app
                                    else
                                        echo "ERROR: auth-service 파일을 찾을 수 없습니다"
                                        exit 1
                                    fi
                                fi

                                if [ -f "../../eks-app/services/user-service.yaml" ]; then
                                    echo "eks-app의 user-service.yaml 사용"
                                    /usr/local/bin/kubectl apply -f ../../eks-app/services/user-service.yaml -n app
                                    echo "User Service (stable/canary) 배포 완료"
                                else
                                    echo "../../eks-app/services/user-service.yaml 파일을 찾을 수 없습니다."
                                    echo "현재 위치에서 user-service 파일 검색:"
                                    find . -name "*user*service*.yaml" -type f
                                    echo "루트에서 user-service 파일 검색:"
                                    find ../../.. -name "user-service.yaml" -type f

                                    # 대체 경로 시도
                                    if [ -f "user-service/user-service-services.yaml" ]; then
                                        echo "대체 파일 사용: user-service/user-service-services.yaml"
                                        /usr/local/bin/kubectl apply -f user-service/user-service-services.yaml -n app
                                    else
                                        echo "ERROR: user-service 파일을 찾을 수 없습니다"
                                        exit 1
                                    fi
                                fi

                                # 2. ALB 대기
                                echo "2. ALB Controller 대기 (30초)..."
                                sleep 30

                                # 3. Ingress 배포
                                echo "3. Ingress 배포..."
                                /usr/local/bin/kubectl apply -f ../../eks-app/ingress/app-ingress.yaml -n app

                                # 4. Analysis Templates 배포
                                echo "4. Analysis Templates 배포..."
                                if [ -f "auth-service/auth-service-analysis.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f auth-service/auth-service-analysis.yaml -n app
                                fi
                                if [ -f "user-service/user-service-analysis.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f user-service/user-service-analysis.yaml -n app
                                fi

                                # 5. Rollout 배포
                                echo "5. Rollout 배포..."
                                mkdir -p /tmp/k8s
                                substitute_vars auth-service/auth-service-rollout.yaml /tmp/k8s/auth-rollout.yaml
                                substitute_vars user-service/user-service-rollout.yaml /tmp/k8s/user-rollout.yaml

                                /usr/local/bin/kubectl apply -f /tmp/k8s/auth-rollout.yaml -n app
                                /usr/local/bin/kubectl apply -f /tmp/k8s/user-rollout.yaml -n app

                                echo "카나리 배포 완료!"
                                ;;
                            *)
                                echo "=== 일반 배포 ==="
                                cd aws/canary-deployment/services

                                /usr/local/bin/kubectl apply -f ../../eks-app/services/auth-service.yaml -n app
                                /usr/local/bin/kubectl apply -f ../../eks-app/services/user-service.yaml -n app
                                sleep 20
                                /usr/local/bin/kubectl apply -f ../../eks-app/ingress/app-ingress.yaml -n app

                                mkdir -p /tmp/k8s
                                substitute_vars auth-service/auth-service-rollout.yaml /tmp/k8s/auth-rollout.yaml
                                substitute_vars user-service/user-service-rollout.yaml /tmp/k8s/user-rollout.yaml

                                /usr/local/bin/kubectl apply -f /tmp/k8s/auth-rollout.yaml -n app
                                /usr/local/bin/kubectl apply -f /tmp/k8s/user-rollout.yaml -n app
                                ;;
                        esac
                    '''
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                sh '''
                    echo "=== 배포 검증 ==="
                    echo "배포 전략: ${DEPLOYMENT_STRATEGY}"

                    echo "Services:"
                    /usr/local/bin/kubectl get svc -n app | grep -E "(auth|user)" || true

                    echo "Endpoints:"
                    /usr/local/bin/kubectl get endpoints -n app | grep -E "(auth|user)" || true

                    echo "Pods:"
                    /usr/local/bin/kubectl get pods -n app || true

                    if [ "${DEPLOYMENT_STRATEGY}" = "canary" ]; then
                        echo "Rollouts:"
                        /usr/local/bin/kubectl get rollouts -n app || true
                        echo "Analysis Templates:"
                        /usr/local/bin/kubectl get analysistemplates -n app || true
                    fi
                '''
            }
        }
    }

    post {
        success {
            script {
                def message = ""
                switch(env.DEPLOYMENT_STRATEGY) {
                    case 'canary':
                        message = """
카나리 배포 완료! (cloud 브랜치)
- 브랜치: ${env.BRANCH_NAME_CLEAN}
- 이미지: canary 태그
- ALB 대상그룹 최적화 적용

모니터링:
kubectl argo rollouts get rollout auth-service-rollout -n app --watch
kubectl argo rollouts get rollout user-service-rollout -n app --watch
"""
                        break
                    default:
                        message = """
배포 완료!
- 브랜치: ${env.BRANCH_NAME_CLEAN}
- 전략: ${env.DEPLOYMENT_STRATEGY}
- 이미지: ${env.IMAGE_TAG}
"""
                }
                echo message
            }
        }

        failure {
            echo "배포 실패! 브랜치: ${env.BRANCH_NAME_CLEAN}, 태그: ${env.IMAGE_TAG}"
        }

        always {
            sh 'rm -rf /tmp/k8s 2>/dev/null || true'
        }
    }
}