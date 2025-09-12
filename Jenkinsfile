pipeline {
    agent any
    
    triggers {
        pollSCM('H/2 * * * *')  // 2분마다 GitHub 변경사항 체크
    }
    
    environment {
        // Git 커밋 해시
        GIT_COMMIT_SHORT = sh(
            script: 'git rev-parse --short=7 HEAD',
            returnStdout: true
        ).trim()
    }

    stages {
        stage('Generate Tags') {
            steps {
                script {
                    // 현재 브랜치 확인
                    def currentBranch = env.GIT_BRANCH ?: env.BRANCH_NAME ?: 'unknown'
                    def branchNameClean = currentBranch.replaceAll('^origin/', '').replaceAll('^refs/heads/', '')
                    
                    echo "DEBUG - 원본 브랜치: ${currentBranch}"
                    echo "DEBUG - 정리된 브랜치: ${branchNameClean}"
                    
                    // 이미지 태그 생성 전략
                    def imageTag
                    def deploymentStrategy

                    if (branchNameClean in ['main', 'master']) {
                        imageTag = "v${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                        deploymentStrategy = "production"
                    } else if (branchNameClean in ['cloud', 'cloud-deploy']) {
                        imageTag = "canary-${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                        deploymentStrategy = "canary"
                    } else if (branchNameClean in ['dev', 'develop', 'development']) {
                        imageTag = "dev-${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                        deploymentStrategy = "development"
                    } else {
                        imageTag = "${branchNameClean}-${env.GIT_COMMIT_SHORT}"
                        deploymentStrategy = "feature"
                    }

                    // 환경변수 설정
                    env.BRANCH_NAME_CLEAN = branchNameClean
                    env.IMAGE_TAG = imageTag
                    env.DEPLOYMENT_STRATEGY = deploymentStrategy

                    echo "=== 브랜치 전략 정보 ==="
                    echo "브랜치: ${branchNameClean}"
                    echo "배포 전략: ${deploymentStrategy}"
                    echo "이미지 태그: ${imageTag}"
                    echo "커밋: ${env.GIT_COMMIT_SHORT}"
                }
            }
        }

        stage('Checkout') {
            steps {
                echo "코드 체크아웃 중..."
                echo "브랜치: ${env.BRANCH_NAME_CLEAN}"
                echo "이미지 태그: ${env.IMAGE_TAG}"
                checkout scm
            }
        }

        stage('Load Configuration') {
            steps {
                echo "환경 설정 로드 중..."
                configFileProvider([configFile(fileId: 'all-services', variable: 'CONFIG_FILE')]) {
                    sh '''
                        . $CONFIG_FILE
                        echo "AWS Region: $AWS_REGION"
                        echo "Image Tag: ${IMAGE_TAG}"

                        /usr/local/bin/aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID
                        /usr/local/bin/aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY
                        /usr/local/bin/aws configure set default.region $AWS_REGION
                        
                        /usr/local/bin/aws sts get-caller-identity
                    '''
                }
            }
        }

        stage('Create ConfigMaps and Secrets') {
            steps {
                echo "ConfigMap 및 Secret 생성 중..."
                configFileProvider([configFile(fileId: 'all-services', variable: 'CONFIG_FILE')]) {
                    sh '''
                        . $CONFIG_FILE
                        
                        echo "=== ConfigMap/Secret 생성 - ${DEPLOYMENT_STRATEGY} 환경 ==="
                        echo "네임스페이스: app (단일 네임스페이스 사용)"

                        # app 네임스페이스 생성 (모든 환경에서 동일하게 사용)
                        /usr/local/bin/kubectl create namespace app --dry-run=client -o yaml | \\
                            /usr/local/bin/kubectl apply -f -

                        # 임시 properties 파일 생성
                        mkdir -p /tmp/k8s-config
                        
                        echo "=== 공통 ConfigMap 생성 (모든 서비스 공유) ==="
                        # 공통 ConfigMap - 모든 서비스가 공유하는 설정
                        cat > /tmp/k8s-config/common-config.properties << EOF
# Spring 기본 설정
SPRING_PROFILES_ACTIVE=production
DEPLOYMENT_STRATEGY=${DEPLOYMENT_STRATEGY}
SPRING_CLOUD_CONFIG_ENABLED=false
EUREKA_CLIENT_ENABLED=false

# 데이터베이스 설정
SPRING_DATASOURCE_URL=${DB_URL}

# Redis 설정 (Auth, User 서비스 공통)
SPRING_REDIS_HOST=${REDIS_HOST}
SPRING_REDIS_PORT=${REDIS_PORT}

# JWT 설정
JWT_EXPIRATION=3600000

# 이메일 설정 (Auth, User 서비스 공통)
SPRING_MAIL_USERNAME=${MAIL_USERNAME}

# Google OAuth 설정 (Auth, User 서비스 공통)
GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}

# 도메인 설정
DOMAIN=${DOMAIN}
EOF

                        echo "=== 공통 Secret 생성 (모든 서비스 공유) ==="
                        # 공통 Secret - 모든 서비스가 공유하는 민감한 정보
                        cat > /tmp/k8s-config/common-secrets.properties << EOF
# 데이터베이스 인증 정보
SPRING_DATASOURCE_USERNAME=${DB_USERNAME}
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}

# JWT 시크릿 (Auth, User 서비스 공통)
JWT_SECRET=${JWT_SECRET}
JWT_REFRESH_SECRET=${JWT_REFRESH_SECRET}

# 이메일 인증 정보 (Auth, User 서비스 공통)
SPRING_MAIL_PASSWORD=${MAIL_PASSWORD}

# Google OAuth 시크릿 (Auth, User 서비스 공통)
GOOGLE_CLIENT_SECRET_ID=${GOOGLE_CLIENT_SECRET_ID}

# AWS 인증 정보
AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
EOF

                        echo "=== Auth Service 전용 ConfigMap 생성 ==="
                        # Auth Service 전용 ConfigMap
                        cat > /tmp/k8s-config/auth-service-config.properties << EOF
# Auth Service 특화 설정
AUTH_SERVICE_PORT=${AUTH_SERVICE_PORT}
AUTH_SERVICE_HEALTH_CHECK_PATH=${AUTH_SERVICE_HEALTH_CHECK_PATH}
EOF

                        echo "=== Auth Service 전용 Secret 생성 ==="
                        # Auth Service 전용 Secret
                        cat > /tmp/k8s-config/auth-service-secrets.properties << EOF
# Auth Service 전용 패스포트 시크릿
PASSPORT_SECRET=${PASSPORT_SECRET}
AUTH_SERVICE_PASSPORT_SECRET=${PASSPORT_SECRET}
EOF

                        echo "=== User Service 전용 ConfigMap 생성 ==="
                        # User Service 전용 ConfigMap
                        cat > /tmp/k8s-config/user-service-config.properties << EOF
# User Service 특화 설정
USER_SERVICE_PORT=${USER_SERVICE_PORT}
USER_SERVICE_HEALTH_CHECK_PATH=${USER_SERVICE_HEALTH_CHECK_PATH}
EOF

                        echo "=== User Service 전용 Secret 생성 ==="
                        # User Service 전용 Secret (현재는 비어있지만 필요시 추가)
                        cat > /tmp/k8s-config/user-service-secrets.properties << EOF
# User Service 전용 시크릿 (필요시 추가)
# USER_SERVICE_SPECIFIC_SECRET=value
EOF

                        echo "=== app 네임스페이스에 ConfigMap/Secret 적용 ==="
                        # 공통 ConfigMap 생성
                        /usr/local/bin/kubectl create configmap common-config -n app \\
                          --from-env-file=/tmp/k8s-config/common-config.properties \\
                          --dry-run=client -o yaml | /usr/local/bin/kubectl apply -f -

                        # 공통 Secret 생성
                        /usr/local/bin/kubectl create secret generic common-secrets -n app \\
                          --from-env-file=/tmp/k8s-config/common-secrets.properties \\
                          --dry-run=client -o yaml | /usr/local/bin/kubectl apply -f -

                        # Auth Service ConfigMap 생성
                        /usr/local/bin/kubectl create configmap auth-service-config -n app \\
                          --from-env-file=/tmp/k8s-config/auth-service-config.properties \\
                          --dry-run=client -o yaml | /usr/local/bin/kubectl apply -f -

                        # Auth Service Secret 생성
                        /usr/local/bin/kubectl create secret generic auth-service-secret -n app \\
                          --from-env-file=/tmp/k8s-config/auth-service-secrets.properties \\
                          --dry-run=client -o yaml | /usr/local/bin/kubectl apply -f -

                        # User Service ConfigMap 생성
                        /usr/local/bin/kubectl create configmap user-service-config -n app \\
                          --from-env-file=/tmp/k8s-config/user-service-config.properties \\
                          --dry-run=client -o yaml | /usr/local/bin/kubectl apply -f -

                        # User Service Secret 생성 (현재는 비어있지만 구조 유지)
                        /usr/local/bin/kubectl create secret generic user-service-secret -n app \\
                          --from-env-file=/tmp/k8s-config/user-service-secrets.properties \\
                          --dry-run=client -o yaml | /usr/local/bin/kubectl apply -f -

                        echo "=== ConfigMap/Secret 생성 완료 ==="
                        echo ""
                        echo "생성된 ConfigMap 목록:"
                        /usr/local/bin/kubectl get configmap -n app | grep -E "(common-config|auth-service|user-service)" || true
                        echo ""
                        echo "생성된 Secret 목록:"
                        /usr/local/bin/kubectl get secret -n app | grep -E "(common-secrets|auth-service|user-service)" || true
                        echo ""
                        echo "공통 ConfigMap 내용 (일부):"
                        /usr/local/bin/kubectl get configmap common-config -n app -o yaml | head -20 || true
                        echo ""
                        echo "Auth Service ConfigMap 내용:"
                        /usr/local/bin/kubectl get configmap auth-service-config -n app -o yaml | head -15 || true
                        
                        # 임시 파일 정리
                        rm -rf /tmp/k8s-config
                        
                        echo "ConfigMap/Secret 생성 및 적용 완료! (app 네임스페이스)"
                    '''
                }
            }
        }

        stage('Start Docker Daemon') {
            steps {
                echo "Docker 데몬 시작 중..."
                sh '''
                    pkill -f dockerd || true
                    sleep 5
                    dockerd --host=unix:///var/run/docker.sock --host=tcp://0.0.0.0:2376 &
                    sleep 30
                    
                    for i in {1..10}; do
                        if docker version >/dev/null 2>&1; then
                            echo "Docker 데몬 연결 성공!"
                            break
                        else
                            echo "Docker 데몬 연결 시도 $i/10..."
                            sleep 5
                        fi
                    done
                    
                    docker version
                '''
            }
        }

        stage('ECR Login') {
            steps {
                echo "ECR 로그인 중..."
                configFileProvider([configFile(fileId: 'all-services', variable: 'CONFIG_FILE')]) {
                    sh '''
                        . $CONFIG_FILE
                        export ECR_REGISTRY=$${AWS_ACCOUNT_ID}.dkr.ecr.$${AWS_REGION}.amazonaws.com
                        
                        /usr/local/bin/aws ecr get-login-password --region $AWS_REGION | \\
                            docker login --username AWS --password-stdin $ECR_REGISTRY
                    '''
                }
            }
        }

        stage('Build Services') {
            steps {
                echo "서비스 빌드 시작 - ${env.DEPLOYMENT_STRATEGY} 환경"
                configFileProvider([configFile(fileId: 'all-services', variable: 'CONFIG_FILE')]) {
                    sh '''
                        . $CONFIG_FILE
                        export ECR_REGISTRY=$${AWS_ACCOUNT_ID}.dkr.ecr.$${AWS_REGION}.amazonaws.com
                        
                        echo "=== 빌드 정보 ==="
                        echo "브랜치: ${BRANCH_NAME_CLEAN}"
                        echo "배포 전략: ${DEPLOYMENT_STRATEGY}"
                        echo "이미지 태그: ${IMAGE_TAG}"

                        chmod +x ./gradlew
                        ./gradlew clean --no-daemon
                        
                        # 브랜치별 태그 전략 함수
                        apply_deployment_tags() {
                            local service_name="\$1"
                            local image_path="$${ECR_REGISTRY}/$${ECR_PREFIX}/${service_name}"
                            
                            echo "=== ${service_name} 태그 적용 ==="

                            case "${DEPLOYMENT_STRATEGY}" in
                                production)
                                    echo "프로덕션 배포 - latest, stable 태그 추가"
                                    docker tag $${image_path}:$${IMAGE_TAG} ${image_path}:latest
                                    docker tag $${image_path}:$${IMAGE_TAG} ${image_path}:stable
                                    docker push ${image_path}:latest
                                    docker push ${image_path}:stable
                                    ;;
                                canary)
                                    echo "카나리 배포 - canary 태그 추가"
                                    docker tag $${image_path}:$${IMAGE_TAG} ${image_path}:canary
                                    docker push ${image_path}:canary
                                    ;;
                                development)
                                    echo "개발 배포 - dev 태그 추가"
                                    docker tag $${image_path}:$${IMAGE_TAG} ${image_path}:dev
                                    docker push ${image_path}:dev
                                    ;;
                                feature)
                                    echo "피처 브랜치 - 기본 태그만"
                                    ;;
                            esac
                        }
                        
                        # Auth Service 빌드
                        echo "=== Auth Service 빌드 ==="
                        ./gradlew :auth-service:build -x test --no-daemon
                        docker build -f ./auth-service/Dockerfile \\
                            -t $${ECR_REGISTRY}/$${ECR_PREFIX}/auth-service:${IMAGE_TAG} .
                        docker push $${ECR_REGISTRY}/$${ECR_PREFIX}/auth-service:${IMAGE_TAG}
                        apply_deployment_tags "auth-service"

                        # User Service 빌드
                        echo "=== User Service 빌드 ==="
                        ./gradlew :user-service:build -x test --no-daemon
                        docker build -f ./user-service/Dockerfile \\
                            -t $${ECR_REGISTRY}/$${ECR_PREFIX}/user-service:${IMAGE_TAG} .
                        docker push $${ECR_REGISTRY}/$${ECR_PREFIX}/user-service:${IMAGE_TAG}
                        apply_deployment_tags "user-service"

                        echo "=== 빌드 완료 ==="
                    '''
                }
            }
        }

        stage('Deploy Services') {
            steps {
                echo "서비스 배포 시작 - ${env.DEPLOYMENT_STRATEGY} 전략"
                configFileProvider([configFile(fileId: 'all-services', variable: 'CONFIG_FILE')]) {
                    sh '''
                        . $CONFIG_FILE
                        export ECR_REGISTRY=$${AWS_ACCOUNT_ID}.dkr.ecr.$${AWS_REGION}.amazonaws.com
                        
                        echo "=== 배포 전략: ${DEPLOYMENT_STRATEGY} ==="
                        echo "네임스페이스: app (단일 네임스페이스 사용)"
                        
                        # sed를 사용한 환경변수 치환 함수 (이미지 정보만 처리)
                        substitute_vars() {
                            local input_file="\$1"
                            local output_file="\$2"

                            # 카나리 배포에서는 IMAGE_TAG를 "canary"로 고정
                            local ACTUAL_IMAGE_TAG="${IMAGE_TAG}"
                            if [ "${DEPLOYMENT_STRATEGY}" = "canary" ]; then
                                ACTUAL_IMAGE_TAG="canary"
                                echo "카나리 배포: 이미지 태그를 'canary'로 설정"
                            fi

                            echo "sed로 환경변수 치환 중: $$input_file -> $$output_file"
                            echo "사용할 이미지 태그: ${ACTUAL_IMAGE_TAG}"
                            
                            # 이미지 관련 변수만 치환 (환경변수는 ConfigMap에서 처리)
                            sed \\
                                -e "s|\\${ECR_REGISTRY}|${ECR_REGISTRY}|g" \\
                                -e "s|\\${ECR_PREFIX}|${ECR_PREFIX}|g" \\
                                -e "s|\\${IMAGE_TAG}|${ACTUAL_IMAGE_TAG}|g" \\
                                -e "s|\\${DEPLOYMENT_STRATEGY}|${DEPLOYMENT_STRATEGY}|g" \\
                                -e "s|\\${AWS_REGION}|${AWS_REGION}|g" \\
                                -e "s|\\${AWS_ACCOUNT_ID}|${AWS_ACCOUNT_ID}|g" \\
                                "$$input_file" > "$$output_file"

                            echo "치환 완료: $$(wc -l < "$$output_file") 라인"
                        }

                        # 배포 전략별 처리 (모두 app 네임스페이스 사용)
                        case "${DEPLOYMENT_STRATEGY}" in
                            canary)
                                echo "=== 카나리 배포 실행 (cloud 브랜치) ==="
                                cd aws/canary-deployment/services

                                # 1. Ingress 먼저 배포
                                echo "1. Ingress 배포 중..."
                                if [ -f "../eks-app/ingress/app-ingress.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f ../eks-app/ingress/app-ingress.yaml -n app
                                    echo "Ingress 배포 완료"
                                else
                                    echo "WARNING: Ingress 파일을 찾을 수 없습니다"
                                fi

                                # 2. 기존 Deployment 삭제
                                echo "2. 기존 Deployment 확인 및 삭제..."
                                /usr/local/bin/kubectl delete deployment auth-deployment -n app --ignore-not-found
                                /usr/local/bin/kubectl delete deployment user-deployment -n app --ignore-not-found
                                
                                # 3. 환경변수 치환 (이미지 정보만)
                                echo "3. Rollout YAML 준비..."
                                mkdir -p /tmp/k8s
                                substitute_vars auth-service/auth-service-rollout.yaml /tmp/k8s/auth-rollout.yaml
                                substitute_vars user-service/user-service-rollout.yaml /tmp/k8s/user-rollout.yaml

                                # 4. Auth Service 카나리 배포
                                echo "4. Auth Service 카나리 배포..."
                                /usr/local/bin/kubectl apply -f auth-service/auth-service-analysis.yaml -n app
                                /usr/local/bin/kubectl apply -f auth-service/auth-service-services.yaml -n app
                                /usr/local/bin/kubectl apply -f /tmp/k8s/auth-rollout.yaml -n app

                                # 5. User Service 카나리 배포  
                                echo "5. User Service 카나리 배포..."
                                /usr/local/bin/kubectl apply -f user-service/user-service-analysis.yaml -n app
                                /usr/local/bin/kubectl apply -f user-service/user-service-services.yaml -n app
                                /usr/local/bin/kubectl apply -f /tmp/k8s/user-rollout.yaml -n app

                                echo "카나리 배포 시작됨 - ConfigMap/Secret 기반 환경변수 사용"
                                echo "모니터링 명령어:"
                                echo "  kubectl argo rollouts get rollout auth-service-rollout -n app --watch"
                                echo "  kubectl argo rollouts get rollout user-service-rollout -n app --watch"
                                ;;
                            production)
                                echo "=== 프로덕션 배포 (main 브랜치) ==="
                                cd aws/canary-deployment/services

                                # Ingress 배포
                                echo "Ingress 배포 중..."
                                if [ -f "../eks-app/ingress/app-ingress.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f ../eks-app/ingress/app-ingress.yaml -n app
                                fi

                                # 환경변수 치환
                                mkdir -p /tmp/k8s
                                substitute_vars auth-service/auth-service-rollout.yaml /tmp/k8s/auth-rollout.yaml
                                substitute_vars user-service/user-service-rollout.yaml /tmp/k8s/user-rollout.yaml

                                /usr/local/bin/kubectl apply -f auth-service/auth-service-services.yaml -n app
                                /usr/local/bin/kubectl apply -f /tmp/k8s/auth-rollout.yaml -n app
                                /usr/local/bin/kubectl apply -f user-service/user-service-services.yaml -n app
                                /usr/local/bin/kubectl apply -f /tmp/k8s/user-rollout.yaml -n app

                                echo "프로덕션 배포 완료 - ConfigMap/Secret 기반"
                                ;;
                            development|feature)
                                echo "=== ${DEPLOYMENT_STRATEGY} 배포 ==="
                                cd aws/canary-deployment/services
                                
                                mkdir -p /tmp/k8s
                                substitute_vars auth-service/auth-service-rollout.yaml /tmp/k8s/auth-rollout.yaml
                                substitute_vars user-service/user-service-rollout.yaml /tmp/k8s/user-rollout.yaml
                                
                                /usr/local/bin/kubectl apply -f auth-service/auth-service-services.yaml -n app
                                /usr/local/bin/kubectl apply -f /tmp/k8s/auth-rollout.yaml -n app
                                /usr/local/bin/kubectl apply -f user-service/user-service-services.yaml -n app
                                /usr/local/bin/kubectl apply -f /tmp/k8s/user-rollout.yaml -n app
                                
                                echo "${DEPLOYMENT_STRATEGY} 배포 완료 - ConfigMap/Secret 기반"
                                ;;
                        esac
                    '''
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                echo "배포 검증 - ${env.DEPLOYMENT_STRATEGY} 환경"
                sh '''
                    echo "=== app 네임스페이스 배포 상태 확인 ==="
                    echo "배포 전략: ${DEPLOYMENT_STRATEGY}"
                    echo ""
                    
                    echo "공통 ConfigMap/Secret 상태:"
                    /usr/local/bin/kubectl get configmap,secret -n app | grep -E "(common-config|common-secrets)" || true
                    echo ""
                    
                    echo "서비스별 ConfigMap/Secret 상태:"
                    /usr/local/bin/kubectl get configmap,secret -n app | grep -E "(auth-service|user-service)" || true
                    echo ""
                    
                    case "${DEPLOYMENT_STRATEGY}" in
                        canary)
                            echo "Rollout 상태 (카나리 배포):"
                            /usr/local/bin/kubectl get rollouts -n app || true
                            echo ""
                            echo "Auth Service Rollout 상태:"
                            /usr/local/bin/kubectl argo rollouts get rollout auth-service-rollout -n app || true
                            echo ""
                            echo "User Service Rollout 상태:"
                            /usr/local/bin/kubectl argo rollouts get rollout user-service-rollout -n app || true
                            ;;
                        *)
                            echo "전체 리소스 상태:"
                            /usr/local/bin/kubectl get all -n app || true
                            ;;
                    esac
                    
                    echo ""
                    echo "Ingress 상태:"
                    /usr/local/bin/kubectl get ingress -n app || true
                    
                    echo ""
                    echo "=== 배포 완료 정보 ==="
                    echo "브랜치: ${BRANCH_NAME_CLEAN}"
                    echo "배포 전략: ${DEPLOYMENT_STRATEGY}"
                    echo "이미지 태그: ${IMAGE_TAG}"
                    echo "네임스페이스: app (단일 네임스페이스)"
                    echo "환경변수 관리: 공통 ConfigMap/Secret + 서비스별 ConfigMap/Secret"
                '''
            }
        }
    }

    post {
        success {
            script {
                echo "=== 빌드 & 배포 성공! ==="

                def message = ""
                switch(env.DEPLOYMENT_STRATEGY) {
                    case 'canary':
                        message = """
카나리 배포 시작됨! (cloud 브랜치)
- 브랜치: ${env.BRANCH_NAME_CLEAN}
- 이미지: canary 태그  
- 네임스페이스: app
- 단계: 10% → 25% → 50% → 100% 자동 진행
- 모니터링: kubectl argo rollouts get rollout auth-service-rollout -n app --watch
- 수동 진행: kubectl argo rollouts promote auth-service-rollout -n app
"""
                        break
                    case 'production':
                        message = """
프로덕션 배포 완료! (main 브랜치)
- 브랜치: ${env.BRANCH_NAME_CLEAN}
- 이미지: latest, stable 태그
- 네임스페이스: app
- 안정적인 프로덕션 서비스 배포됨
"""
                        break
                    case 'development':
                        message = """
개발환경 배포 완료!
- 브랜치: ${env.BRANCH_NAME_CLEAN}
- 이미지: dev 태그
- 네임스페이스: app
- 개발 테스트 준비 완료
"""
                        break
                    default:
                        message = """
피처 빌드 완료!
- 브랜치: ${env.BRANCH_NAME_CLEAN}
- 이미지: ${env.IMAGE_TAG}
- 네임스페이스: app
"""
                }
                
                echo message
            }
        }
        
        failure {
            script {
                echo "ERROR: 서비스 빌드 파이프라인 실패!"
                echo "실패한 브랜치: ${env.BRANCH_NAME_CLEAN ?: 'unknown'}"
                echo "시도한 태그: ${env.IMAGE_TAG ?: 'unknown'}"
                
                sh '''
                    echo "=== 상세 실패 분석 ==="
                    
                    echo "1. 빌드 아티팩트 상태:"
                    echo "   Auth Service JAR:"
                    find ./auth-service/build -name "*.jar" 2>/dev/null || echo "   Auth Service JAR 없음"
                    echo "   User Service JAR:"
                    find ./user-service/build -name "*.jar" 2>/dev/null || echo "   User Service JAR 없음"
                    
                    echo ""
                    echo "2. 최근 쿠버네티스 이벤트:"
                    /usr/local/bin/kubectl get events -n app --sort-by='.lastTimestamp' | tail -5 || echo "   이벤트 조회 실패"
                    
                    echo ""
                    echo "3. Docker 이미지 상태:"
                    docker images | grep -E "(auth-service|user-service)" | head -10 || echo "   Docker 이미지 없음"
                    
                    echo ""
                    echo "4. ConfigMap/Secret 상태:"
                    /usr/local/bin/kubectl get configmap,secret -n app | grep -E "(common|auth-service|user-service)" || echo "   ConfigMap/Secret 조회 실패"
                    
                    echo ""
                    echo "=== 해결 방법 제안 ==="
                    echo "1. 의존성 문제인 경우: ./gradlew clean 후 재시도"
                    echo "2. Docker 문제인 경우: Docker 데몬 재시작"
                    echo "3. 개별 서비스 빌드 테스트: ./gradlew :auth-service:build -x test"
                    echo "4. ConfigMap/Secret 문제인 경우: kubectl delete configmap,secret --all -n app 후 재시도"
                '''
            }
        }
        
        always {
            script {
                echo "정리 작업 시작"
                sh '''
                    echo "=== 빌드 통계 ==="
                    echo "빌드 번호: ${BUILD_NUMBER}"
                    echo "빌드 시간: $(date)"
                    echo "워크스페이스 크기: $(du -sh ${WORKSPACE} 2>/dev/null || echo '알 수 없음')"
                    echo "네임스페이스: app (단일 네임스페이스 사용)"
                    
                    echo ""
                    echo "=== Docker 정리 ==="
                    # 사용하지 않는 빌드 캐시 정리
                    docker system prune -f || true
                    
                    # 빌드된 이미지 확인 (정리 전)
                    echo "빌드된 이미지들:"
                    docker images | grep -E "(auth-service|user-service)" || echo "빌드된 이미지 없음"
                    
                    echo ""
                    echo "=== Gradle 정리 ==="
                    # 다음 빌드를 위한 정리
                    ./gradlew clean || true
                    
                    echo ""
                    echo "=== 최종 배포 상태 확인 ==="
                    echo "ConfigMap 개수: $(/usr/local/bin/kubectl get configmap -n app --no-headers | wc -l || echo '0')"
                    echo "Secret 개수: $(/usr/local/bin/kubectl get secret -n app --no-headers | wc -l || echo '0')"
                    echo "Pod 개수: $(/usr/local/bin/kubectl get pods -n app --no-headers | wc -l || echo '0')"
                    
                    echo ""
                    echo "정리 완료"
                    echo "파이프라인 종료: $(date)"
                '''
            }
        }
    }
}