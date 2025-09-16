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

        stage('Build and Deploy Services') {
            steps {
                configFileProvider([configFile(fileId: 'all-services', variable: 'CONFIG_FILE')]) {
                    sh '''
                        # 환경 변수 로드
                        set -a
                        source "$CONFIG_FILE"
                        set +a

                        ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                        echo "=== 빌드 및 배포 정보 ==="
                        echo "전략: ${DEPLOYMENT_STRATEGY}"
                        echo "이미지 태그: ${IMAGE_TAG}"
                        echo "ECR 레지스트리: $ECR_REGISTRY"
                        echo "Kafka 엔드포인트: $KAFKA_BOOTSTRAP_SERVERS"

                        # 1. 서비스 빌드
                        echo "=== 서비스 빌드 ==="
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

                        # 2. ConfigMap 및 Secret 생성/업데이트
                        echo "=== ConfigMap 및 Secret 업데이트 ==="

                        # Kafka ConfigMap 생성
                        kubectl create configmap kafka-config \\
                            --from-literal=KAFKA_BOOTSTRAP_SERVERS="$KAFKA_BOOTSTRAP_SERVERS" \\
                            --from-literal=SPRING_KAFKA_BOOTSTRAP_SERVERS="$KAFKA_BOOTSTRAP_SERVERS" \\
                            -n app --dry-run=client -o yaml | kubectl apply -f -

                        # 애플리케이션 전체 설정 Secret 생성
                        kubectl create secret generic app-config \\
                            --from-literal=AWS_REGION="$AWS_REGION" \\
                            --from-literal=DB_URL="$DB_URL" \\
                            --from-literal=DB_USERNAME="$DB_USERNAME" \\
                            --from-literal=DB_PASSWORD="$DB_PASSWORD" \\
                            --from-literal=REDIS_HOST="$REDIS_HOST" \\
                            --from-literal=REDIS_PORT="$REDIS_PORT" \\
                            --from-literal=KAFKA_BOOTSTRAP_SERVERS="$KAFKA_BOOTSTRAP_SERVERS" \\
                            --from-literal=JWT_SECRET="$JWT_SECRET" \\
                            --from-literal=JWT_REFRESH_SECRET="$JWT_REFRESH_SECRET" \\
                            --from-literal=MAIL_USERNAME="$MAIL_USERNAME" \\
                            --from-literal=MAIL_PASSWORD="$MAIL_PASSWORD" \\
                            --from-literal=GOOGLE_CLIENT_ID="$GOOGLE_CLIENT_ID" \\
                            --from-literal=GOOGLE_CLIENT_SECRET_ID="$GOOGLE_CLIENT_SECRET_ID" \\
                            -n app --dry-run=client -o yaml | kubectl apply -f -

                        # 3. 템플릿 치환 함수
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
                                -e "s|\\${KAFKA_BOOTSTRAP_SERVERS}|$KAFKA_BOOTSTRAP_SERVERS|g" \\
                                -e "s|\\${DB_URL}|$DB_URL|g" \\
                                -e "s|\\${DB_USERNAME}|$DB_USERNAME|g" \\
                                -e "s|\\${DB_PASSWORD}|$DB_PASSWORD|g" \\
                                -e "s|\\${REDIS_HOST}|$REDIS_HOST|g" \\
                                -e "s|\\${REDIS_PORT}|$REDIS_PORT|g" \\
                                -e "s|\\${JWT_SECRET}|$JWT_SECRET|g" \\
                                -e "s|\\${JWT_REFRESH_SECRET}|$JWT_REFRESH_SECRET|g" \\
                                "$input_file" > "$output_file"
                        }

                        # 4. 배포 전략에 따른 배포
                        case "${DEPLOYMENT_STRATEGY}" in
                            canary)
                                echo "=== 카나리 배포 ==="
                                cd aws/canary-deployment/services

                                # Services 배포
                                echo "Services 배포..."
                                /usr/local/bin/kubectl apply -f ../../eks-app/services/auth-service.yaml -n app
                                /usr/local/bin/kubectl apply -f ../../eks-app/services/user-service.yaml -n app

                                # 기존 Ingress 확인 (새로 배포하지 않고 참조만)
                                echo "기존 Ingress 확인..."
                                if ! kubectl get ingress app-ingress -n app &>/dev/null; then
                                    echo "Ingress가 없으므로 새로 생성합니다."
                                    /usr/local/bin/kubectl apply -f ../../eks-app/ingress/app-ingress.yaml -n app
                                    sleep 30  # ALB 생성 대기
                                else
                                    echo "기존 Ingress를 사용합니다."
                                fi

                                # Analysis Templates 배포
                                echo "Analysis Templates 배포..."
                                [ -f "auth-service/auth-service-analysis.yaml" ] && /usr/local/bin/kubectl apply -f auth-service/auth-service-analysis.yaml -n app
                                [ -f "user-service/user-service-analysis.yaml" ] && /usr/local/bin/kubectl apply -f user-service/user-service-analysis.yaml -n app

                                # Rollout 배포
                                echo "Rollout 배포..."
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

                                # Services 배포
                                /usr/local/bin/kubectl apply -f ../../eks-app/services/auth-service.yaml -n app
                                /usr/local/bin/kubectl apply -f ../../eks-app/services/user-service.yaml -n app

                                # 기존 Ingress 확인
                                if ! kubectl get ingress app-ingress -n app &>/dev/null; then
                                    echo "Ingress가 없으므로 새로 생성합니다."
                                    /usr/local/bin/kubectl apply -f ../../eks-app/ingress/app-ingress.yaml -n app
                                    sleep 20
                                else
                                    echo "기존 Ingress를 사용합니다."
                                fi

                                # Rollout 배포
                                mkdir -p /tmp/k8s
                                substitute_vars auth-service/auth-service-rollout.yaml /tmp/k8s/auth-rollout.yaml
                                substitute_vars user-service/user-service-rollout.yaml /tmp/k8s/user-rollout.yaml

                                /usr/local/bin/kubectl apply -f /tmp/k8s/auth-rollout.yaml -n app
                                /usr/local/bin/kubectl apply -f /tmp/k8s/user-rollout.yaml -n app
                                ;;
                        esac

                        # 5. 배포 상태 확인
                        echo "=== 배포 상태 확인 ==="
                        kubectl get pods -n app | grep -E "(auth-service|user-service)"
                        kubectl get rollouts -n app || echo "Argo Rollouts이 설치되지 않았을 수 있습니다."
                        '''
                }
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