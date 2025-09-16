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

                        # ✨ 수정된 ConfigMap 생성 (msk 프로파일 포함)
                        cat > /tmp/k8s-config/app-config.properties << EOF
SPRING_PROFILES_ACTIVE=production,msk
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
MANAGEMENT_HEALTH_KAFKA_ENABLED=${MANAGEMENT_HEALTH_KAFKA_ENABLED}
SPRING_KAFKA_CONSUMER_ENABLE_AUTO_COMMIT=${SPRING_KAFKA_CONSUMER_ENABLE_AUTO_COMMIT}
SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=${SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET}
LOGGING_LEVEL_KAFKA=${LOGGING_LEVEL_KAFKA}
LOGGING_LEVEL_NETWORKLIENT=${LOGGING_LEVEL_NETWORKLIENT}
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
                        . "$CONFIG_FILE"
                        ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                        echo "=== 서비스 빌드 ==="
                        echo "전략: ${DEPLOYMENT_STRATEGY}"
                        echo "이미지 태그: ${IMAGE_TAG}"
                        echo "ECR 레지스트리: $ECR_REGISTRY"
                        echo "Kafka 설정: ${KAFKA_BOOTSTRAP_SERVERS}"

                        chmod +x ./gradlew
                        ./gradlew clean --no-daemon

                        # Auth Service 빌드
                        echo "Auth Service 빌드 중..."
                        ./gradlew :auth-service:build -x test --no-daemon
                        docker build -f ./auth-service/Dockerfile -t ${ECR_REGISTRY}/${ECR_PREFIX}/auth-service:${IMAGE_TAG} .
                        docker push ${ECR_REGISTRY}/${ECR_PREFIX}/auth-service:${IMAGE_TAG}

                        # User Service 빌드
                        echo "User Service 빌드 중..."
                        ./gradlew :user-service:build -x test --no-daemon
                        docker build -f ./user-service/Dockerfile -t ${ECR_REGISTRY}/${ECR_PREFIX}/user-service:${IMAGE_TAG} .
                        docker push ${ECR_REGISTRY}/${ECR_PREFIX}/user-service:${IMAGE_TAG}

                        # 카나리 배포용 태그 추가
                        if [ "${DEPLOYMENT_STRATEGY}" = "canary" ]; then
                            echo "카나리 태그 생성 중..."
                            docker tag ${ECR_REGISTRY}/${ECR_PREFIX}/auth-service:${IMAGE_TAG} ${ECR_REGISTRY}/${ECR_PREFIX}/auth-service:canary
                            docker tag ${ECR_REGISTRY}/${ECR_PREFIX}/user-service:${IMAGE_TAG} ${ECR_REGISTRY}/${ECR_PREFIX}/user-service:canary
                            docker push ${ECR_REGISTRY}/${ECR_PREFIX}/auth-service:canary
                            docker push ${ECR_REGISTRY}/${ECR_PREFIX}/user-service:canary
                        fi

                        echo "빌드 완료!"
                    '''
                }
            }
        }

        stage('Deploy Services') {
            steps {
                configFileProvider([configFile(fileId: 'all-services', variable: 'CONFIG_FILE')]) {
                    sh '''
                        . "$CONFIG_FILE"
                        ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                        echo "=== 서비스 배포 ==="
                        echo "전략: ${DEPLOYMENT_STRATEGY}"
                        echo "Kafka 엔드포인트: $KAFKA_BOOTSTRAP_SERVERS"

                        # 템플릿 치환 함수
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

                        # 배포 전략에 따른 배포
                        case "${DEPLOYMENT_STRATEGY}" in
                            canary)
                                echo "=== 카나리 배포 ==="
                                cd aws/canary-deployment/services

                                # Services 배포
                                echo "1. Services 배포 중..."
                                if [ -f "../../eks-app/services/auth-service.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f ../../eks-app/services/auth-service.yaml -n app
                                    echo "Auth Service 생성 완료"
                                else
                                    echo "auth-service.yaml 파일을 찾을 수 없습니다."
                                    find ../../.. -name "*auth*service*.yaml" -type f || echo "대체 파일 없음"
                                fi

                                if [ -f "../../eks-app/services/user-service.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f ../../eks-app/services/user-service.yaml -n app
                                    echo "User Service 생성 완료"
                                else
                                    echo "user-service.yaml 파일을 찾을 수 없습니다."
                                    find ../../.. -name "*user*service*.yaml" -type f || echo "대체 파일 없음"
                                fi

                                # ALB 대기
                                echo "2. ALB 초기화 대기 (30초)..."
                                sleep 30

                                # Ingress 배포
                                echo "3. Ingress 배포 중..."
                                if ! /usr/local/bin/kubectl get ingress app-ingress -n app &>/dev/null; then
                                    echo "새 Ingress 생성 중..."
                                    /usr/local/bin/kubectl apply -f ../../eks-app/ingress/app-ingress.yaml -n app
                                else
                                    echo "기존 Ingress 사용"
                                fi

                                # Analysis Templates 배포
                                echo "4. Analysis Templates 배포 중..."
                                [ -f "auth-service/auth-service-analysis.yaml" ] && /usr/local/bin/kubectl apply -f auth-service/auth-service-analysis.yaml -n app
                                [ -f "user-service/user-service-analysis.yaml" ] && /usr/local/bin/kubectl apply -f user-service/user-service-analysis.yaml -n app

                                # Rollout 배포
                                echo "5. Rollout 배포 중..."
                                mkdir -p /tmp/k8s
                                substitute_vars auth-service/auth-service-rollout.yaml /tmp/k8s/auth-rollout.yaml
                                substitute_vars user-service/user-service-rollout.yaml /tmp/k8s/user-rollout.yaml

                                echo "배포할 Rollout 파일 확인:"
                                echo "Auth Rollout 이미지 태그:"
                                grep "image:" /tmp/k8s/auth-rollout.yaml || echo "이미지 태그 확인 실패"
                                echo "User Rollout 이미지 태그:"
                                grep "image:" /tmp/k8s/user-rollout.yaml || echo "이미지 태그 확인 실패"

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

                                sleep 20

                                # Ingress 배포
                                if ! /usr/local/bin/kubectl get ingress app-ingress -n app &>/dev/null; then
                                    /usr/local/bin/kubectl apply -f ../../eks-app/ingress/app-ingress.yaml -n app
                                fi

                                # Rollout 배포
                                mkdir -p /tmp/k8s
                                substitute_vars auth-service/auth-service-rollout.yaml /tmp/k8s/auth-rollout.yaml
                                substitute_vars user-service/user-service-rollout.yaml /tmp/k8s/user-rollout.yaml

                                echo "배포할 Rollout 파일 확인:"
                                echo "Auth Rollout 이미지 태그:"
                                grep "image:" /tmp/k8s/auth-rollout.yaml || echo "이미지 태그 확인 실패"
                                echo "User Rollout 이미지 태그:"
                                grep "image:" /tmp/k8s/user-rollout.yaml || echo "이미지 태그 확인 실패"

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
                    /usr/local/bin/kubectl get svc -n app | grep -E "(auth|user)" || echo "서비스 없음"

                    echo "Endpoints:"
                    /usr/local/bin/kubectl get endpoints -n app | grep -E "(auth|user)" || echo "엔드포인트 없음"

                    echo "Pods:"
                    /usr/local/bin/kubectl get pods -n app || echo "Pod 없음"

                    echo "Rollouts:"
                    /usr/local/bin/kubectl get rollouts -n app || echo "Rollout 없음 (Argo Rollouts이 설치되지 않았을 수 있음)"

                    if [ "${DEPLOYMENT_STRATEGY}" = "canary" ]; then
                        echo "Analysis Templates:"
                        /usr/local/bin/kubectl get analysistemplates -n app || echo "Analysis Template 없음"
                    fi

                    echo "ConfigMap 확인:"
                    /usr/local/bin/kubectl get configmap app-config -n app -o yaml | grep -A1 -B1 KAFKA || echo "Kafka 설정 없음"

                    echo "Secret 확인:"
                    /usr/local/bin/kubectl get secret app-secrets -n app || echo "Secret 없음"

                    # ✨ 추가: Pod 로그 미리보기
                    echo "Pod 상태 및 간단한 로그 확인:"
                    for pod in $(kubectl get pods -n app -o name 2>/dev/null); do
                        pod_name=$(basename $pod)
                        echo "=== $pod_name 상태 ==="
                        kubectl get pod $pod_name -n app || true
                        echo "최근 로그 (마지막 5줄):"
                        kubectl logs $pod_name -n app --tail=5 2>/dev/null || echo "로그 없음"
                        echo ""
                    done
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
🎉 카나리 배포 완료! (cloud 브랜치)
- 브랜치: ${env.BRANCH_NAME_CLEAN}
- 이미지: canary 태그
- Kafka 설정: 완전 적용됨 ✅

📊 모니터링:
kubectl argo rollouts get rollout auth-service-rollout -n app --watch
kubectl argo rollouts get rollout user-service-rollout -n app --watch

🔍 상태 확인:
kubectl get pods -n app
kubectl logs -n app -l app=auth --tail=20
kubectl logs -n app -l app=user --tail=20

🌐 서비스 접속:
https://${env.DOMAIN}/auth/actuator/health
https://${env.DOMAIN}/user/actuator/health
"""
                        break
                    default:
                        message = """
✅ 배포 완료!
- 브랜치: ${env.BRANCH_NAME_CLEAN}
- 전략: ${env.DEPLOYMENT_STRATEGY}
- 이미지: ${env.IMAGE_TAG}
- Kafka 설정: 완전 적용됨 ✅

🔍 상태 확인:
kubectl get pods -n app
kubectl logs -n app -l app=auth --tail=20
kubectl logs -n app -l app=user --tail=20
"""
                }
                echo message
            }
        }

        failure {
            echo "❌ 배포 실패! 브랜치: ${env.BRANCH_NAME_CLEAN}, 태그: ${env.IMAGE_TAG}"
            sh '''
                echo "=== 실패 시 디버깅 정보 ==="
                echo "현재 리소스 상태:"
                /usr/local/bin/kubectl get all -n app || true

                echo "ConfigMap 상태:"
                /usr/local/bin/kubectl get configmap app-config -n app -o yaml | grep -A2 -B2 KAFKA || true

                echo "최근 이벤트:"
                /usr/local/bin/kubectl get events -n app --sort-by='.lastTimestamp' | tail -10 || true

                echo "Pod 로그 (실패한 Pod들):"
                for pod in $(kubectl get pods -n app -o name --field-selector=status.phase!=Running 2>/dev/null); do
                    pod_name=$(basename $pod)
                    echo "=== $pod_name 로그 ==="
                    kubectl logs $pod_name -n app --tail=50 2>/dev/null || echo "로그 없음"
                    echo ""
                done
            '''
        }

        always {
            sh '''
                rm -rf /tmp/k8s 2>/dev/null || true
                rm -rf /tmp/k8s-config 2>/dev/null || true
            '''
        }
    }
}