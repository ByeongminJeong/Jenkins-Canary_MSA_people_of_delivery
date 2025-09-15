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

    stage('Generate Tags') {
        steps {
            script {
                // 현재 브랜치 확인
                def currentBranch = env.GIT_BRANCH ?: env.BRANCH_NAME ?: 'unknown'
                def branchNameClean = currentBranch.replaceAll('^origin/', '').replaceAll('^refs/heads/', '')

                echo "DEBUG - 원본 브랜치: ${currentBranch}"
                echo "DEBUG - 정리된 브랜치: ${branchNameClean}"

                // 이미지 태그 생성
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
                echo "통합 ConfigMap 및 Secret 생성 중..."
                configFileProvider([configFile(fileId: 'all-services', variable: 'CONFIG_FILE')]) {
                    sh '''
                        . $CONFIG_FILE
                        
                        echo "=== 통합 ConfigMap/Secret 생성 - ${DEPLOYMENT_STRATEGY} 환경 ==="
                        echo "네임스페이스: app (단일 네임스페이스 사용)"

                        # app 네임스페이스 생성
                        /usr/local/bin/kubectl create namespace app --dry-run=client -o yaml | \\
                            /usr/local/bin/kubectl apply -f -

                        # 임시 properties 파일 생성
                        mkdir -p /tmp/k8s-config
                        
                        echo "=== 통합 ConfigMap 생성 (Spring Cloud 제거) ==="
                        cat > /tmp/k8s-config/app-config.properties << EOF
# Spring 기본 설정 (Cloud 제거)
SPRING_PROFILES_ACTIVE=production
DEPLOYMENT_STRATEGY=${DEPLOYMENT_STRATEGY}

# Eureka 완전 비활성화
EUREKA_CLIENT_ENABLED=false
EUREKA_CLIENT_REGISTER_WITH_EUREKA=false
EUREKA_CLIENT_FETCH_REGISTRY=false
SPRING_CLOUD_DISCOVERY_ENABLED=false

# 데이터베이스 설정
SPRING_DATASOURCE_URL=${DB_URL}

# Redis 설정 (두 가지 형태 모두 제공)
SPRING_REDIS_HOST=${REDIS_HOST}
SPRING_REDIS_PORT=${REDIS_PORT}
REDIS_HOST=${REDIS_HOST}
REDIS_PORT=${REDIS_PORT}

# JWT 설정
JWT_EXPIRATION=3600000

# 이메일 설정
SPRING_MAIL_USERNAME=${MAIL_USERNAME}

# Google OAuth 설정
GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}

# 도메인 설정
DOMAIN=${DOMAIN}

# Auth Service 설정
SERVER_PORT=8015
AUTH_SERVICE_PORT=${AUTH_SERVICE_PORT}
AUTH_SERVICE_HEALTH_CHECK_PATH=${AUTH_SERVICE_HEALTH_CHECK_PATH}

# User Service 설정
USER_SERVICE_PORT=${USER_SERVICE_PORT}
USER_SERVICE_HEALTH_CHECK_PATH=${USER_SERVICE_HEALTH_CHECK_PATH}

# Module Common 설정
MODULE_COMMON_SERVICE_PORT=${MODULE_COMMON_SERVICE_PORT}
MODULE_COMMON_SERVICE_HEALTH_CHECK_PATH=${MODULE_COMMON_SERVICE_HEALTH_CHECK_PATH}

# Spring Boot 최적화
SPRING_JPA_OPEN_IN_VIEW=false
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
EOF

                        echo "=== 통합 Secret 생성 ==="
                        cat > /tmp/k8s-config/app-secrets.properties << EOF
# 데이터베이스 인증 정보
SPRING_DATASOURCE_USERNAME=${DB_USERNAME}
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}

# JWT 시크릿
JWT_SECRET=${JWT_SECRET}
JWT_REFRESH_SECRET=${JWT_REFRESH_SECRET}

# 패스포트 시크릿 (Auth Service용)
PASSPORT_SECRET=${PASSPORT_SECRET}
AUTH_SERVICE_PASSPORT_SECRET=${PASSPORT_SECRET}

# 이메일 인증 정보
SPRING_MAIL_PASSWORD=${MAIL_PASSWORD}

# Google OAuth 시크릿
GOOGLE_CLIENT_SECRET_ID=${GOOGLE_CLIENT_SECRET_ID}

# AWS 인증 정보
AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
EOF

                        echo "=== app 네임스페이스에 통합 ConfigMap/Secret 적용 ==="
                        # 통합 ConfigMap 생성
                        /usr/local/bin/kubectl create configmap app-config -n app \\
                          --from-env-file=/tmp/k8s-config/app-config.properties \\
                          --dry-run=client -o yaml | /usr/local/bin/kubectl apply -f -

                        # 통합 Secret 생성
                        /usr/local/bin/kubectl create secret generic app-secrets -n app \\
                          --from-env-file=/tmp/k8s-config/app-secrets.properties \\
                          --dry-run=client -o yaml | /usr/local/bin/kubectl apply -f -

                        echo "=== 통합 ConfigMap/Secret 생성 완료 ==="
                        echo ""
                        echo "생성된 리소스:"
                        /usr/local/bin/kubectl get configmap,secret -n app | grep app- || true
                        echo ""
                        echo "Redis 설정 확인:"
                        /usr/local/bin/kubectl get configmap app-config -n app -o yaml | grep -E "REDIS" || true
                        
                        # 임시 파일 정리
                        rm -rf /tmp/k8s-config
                        
                        echo "통합 ConfigMap/Secret 생성 완료! (Spring Cloud 의존성 제거)"
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

                        # ECR Registry 설정 (올바른 문법)
                        ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                        echo "ECR Registry: $ECR_REGISTRY"
                        echo "AWS Region: $AWS_REGION"
                        echo "AWS Account: $AWS_ACCOUNT_ID"

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

                        # ECR Registry 설정
                        ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                        echo "=== 빌드 정보 ==="
                        echo "브랜치: ${BRANCH_NAME_CLEAN}"
                        echo "배포 전략: ${DEPLOYMENT_STRATEGY}"
                        echo "이미지 태그: ${IMAGE_TAG}"
                        echo "ECR Registry: $ECR_REGISTRY"

                        chmod +x ./gradlew
                        ./gradlew clean --no-daemon

                        # 브랜치별 태그 전략 함수
                        apply_deployment_tags() {
                            local service_name="$1"
                            local image_path="$ECR_REGISTRY/$ECR_PREFIX/$service_name"

                            echo "=== $service_name 태그 적용 ==="

                            case "${DEPLOYMENT_STRATEGY}" in
                                production)
                                    echo "프로덕션 배포 - latest, stable 태그 추가"
                                    docker tag $image_path:${IMAGE_TAG} $image_path:latest
                                    docker tag $image_path:${IMAGE_TAG} $image_path:stable
                                    docker push $image_path:latest
                                    docker push $image_path:stable
                                    ;;
                                canary)
                                    echo "카나리 배포 - canary 태그 추가"
                                    docker tag $image_path:${IMAGE_TAG} $image_path:canary
                                    docker push $image_path:canary
                                    ;;
                                development)
                                    echo "개발 배포 - dev 태그 추가"
                                    docker tag $image_path:${IMAGE_TAG} $image_path:dev
                                    docker push $image_path:dev
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
                            -t $ECR_REGISTRY/$ECR_PREFIX/auth-service:${IMAGE_TAG} .
                        docker push $ECR_REGISTRY/$ECR_PREFIX/auth-service:${IMAGE_TAG}
                        apply_deployment_tags "auth-service"

                        # User Service 빌드
                        echo "=== User Service 빌드 ==="
                        ./gradlew :user-service:build -x test --no-daemon
                        docker build -f ./user-service/Dockerfile \\
                            -t $ECR_REGISTRY/$ECR_PREFIX/user-service:${IMAGE_TAG} .
                        docker push $ECR_REGISTRY/$ECR_PREFIX/user-service:${IMAGE_TAG}
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

                        # ECR Registry 설정 (올바른 문법)
                        ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                        echo "=== 배포 전략: ${DEPLOYMENT_STRATEGY} ==="
                        echo "네임스페이스: app (단일 네임스페이스 사용)"
                        echo "ECR Registry: $ECR_REGISTRY"

                        # sed를 사용한 환경변수 치환 함수
                        substitute_vars() {
                            local input_file="$1"
                            local output_file="$2"

                            # 카나리 배포에서는 IMAGE_TAG를 "canary"로 고정
                            local ACTUAL_IMAGE_TAG="${IMAGE_TAG}"
                            if [ "${DEPLOYMENT_STRATEGY}" = "canary" ]; then
                                ACTUAL_IMAGE_TAG="canary"
                                echo "카나리 배포: 이미지 태그를 'canary'로 설정"
                            fi

                            echo "sed로 환경변수 치환 중: $input_file -> $output_file"
                            echo "ECR_REGISTRY: $ECR_REGISTRY"
                            echo "ECR_PREFIX: $ECR_PREFIX"
                            echo "사용할 이미지 태그: $ACTUAL_IMAGE_TAG"

                            # 환경변수 치환
                            sed \\
                                -e "s|\\${ECR_REGISTRY}|$ECR_REGISTRY|g" \\
                                -e "s|\\${ECR_PREFIX}|$ECR_PREFIX|g" \\
                                -e "s|\\${IMAGE_TAG}|$ACTUAL_IMAGE_TAG|g" \\
                                -e "s|\\${DEPLOYMENT_STRATEGY}|${DEPLOYMENT_STRATEGY}|g" \\
                                -e "s|\\${AWS_REGION}|$AWS_REGION|g" \\
                                -e "s|\\${AWS_ACCOUNT_ID}|$AWS_ACCOUNT_ID|g" \\
                                "$input_file" > "$output_file"

                            echo "치환 완료: $(wc -l < "$output_file") 라인"
                            echo "치환된 이미지 확인:"
                            grep -E "image:" "$output_file" || true
                        }

                        # 배포 전략별 처리 (모두 app 네임스페이스 사용)
                        case "${DEPLOYMENT_STRATEGY}" in
                            canary)
                                echo "=== 카나리 배포 실행 (cloud 브랜치) ==="
                                cd aws/canary-deployment/services

                                # 현재 디렉토리 파일 확인
                                echo "현재 위치: $(pwd)"
                                echo "사용 가능한 파일들:"
                                find . -name "*.yaml" -type f | sort

                                # 1. Ingress 먼저 배포
                                echo "1. Ingress 배포 중..."
                                if [ -f "../../eks-app/ingress/app-ingress.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f ../../eks-app/ingress/app-ingress.yaml -n app
                                    echo "Ingress 배포 완료"
                                else
                                    echo "WARNING: Ingress 파일을 찾을 수 없습니다"
                                fi

                                # 2. 기존 Deployment 삭제
                                echo "2. 기존 Deployment 확인 및 삭제..."
                                /usr/local/bin/kubectl delete deployment auth-deployment -n app --ignore-not-found
                                /usr/local/bin/kubectl delete deployment user-deployment -n app --ignore-not-found

                                # 3. Analysis Templates 먼저 배포
                                echo "3. Analysis Templates 배포..."
                                if [ -f "auth-service/auth-service-analysis.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f auth-service/auth-service-analysis.yaml -n app
                                    echo "Auth Service Analysis Template 배포 완료"
                                else
                                    echo "WARNING: auth-service-analysis.yaml 파일을 찾을 수 없습니다"
                                fi

                                if [ -f "user-service/user-service-analysis.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f user-service/user-service-analysis.yaml -n app
                                    echo "User Service Analysis Template 배포 완료"
                                else
                                    echo "WARNING: user-service-analysis.yaml 파일을 찾을 수 없습니다"
                                fi

                                # 4. 환경변수 치환 (이미지 정보만)
                                echo "4. Rollout YAML 준비..."
                                mkdir -p /tmp/k8s

                                if [ -f "auth-service/auth-service-rollout.yaml" ]; then
                                    substitute_vars auth-service/auth-service-rollout.yaml /tmp/k8s/auth-rollout.yaml
                                    echo "Auth Service Rollout YAML 준비 완료"
                                else
                                    echo "ERROR: auth-service-rollout.yaml 파일을 찾을 수 없습니다"
                                    exit 1
                                fi

                                if [ -f "user-service/user-service-rollout.yaml" ]; then
                                    substitute_vars user-service/user-service-rollout.yaml /tmp/k8s/user-rollout.yaml
                                    echo "User Service Rollout YAML 준비 완료"
                                else
                                    echo "ERROR: user-service-rollout.yaml 파일을 찾을 수 없습니다"
                                    exit 1
                                fi

                                # 5. Services 배포
                                echo "5. Services 배포..."
                                if [ -f "auth-service/auth-service-services.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f auth-service/auth-service-services.yaml -n app
                                    echo "Auth Service Services 배포 완료"
                                else
                                    echo "ERROR: auth-service-services.yaml 파일을 찾을 수 없습니다"
                                fi

                                if [ -f "user-service/user-service-services.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f user-service/user-service-services.yaml -n app
                                    echo "User Service Services 배포 완료"
                                else
                                    echo "ERROR: user-service-services.yaml 파일을 찾을 수 없습니다"
                                fi

                                # 6. Auth Service 카나리 배포
                                echo "6. Auth Service 카나리 Rollout 배포..."
                                if [ -f "/tmp/k8s/auth-rollout.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f /tmp/k8s/auth-rollout.yaml -n app
                                    echo "Auth Service Rollout 배포 완료"
                                else
                                    echo "ERROR: Auth Rollout YAML 파일 생성 실패"
                                fi

                                # 7. User Service 카나리 배포
                                echo "7. User Service 카나리 Rollout 배포..."
                                if [ -f "/tmp/k8s/user-rollout.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f /tmp/k8s/user-rollout.yaml -n app
                                    echo "User Service Rollout 배포 완료"
                                else
                                    echo "ERROR: User Rollout YAML 파일 생성 실패"
                                fi

                                echo "=== 카나리 배포 시작됨! ==="
                                echo "Analysis Templates 포함된 카나리 배포가 시작되었습니다."
                                echo ""
                                echo "모니터링 명령어:"
                                echo "  kubectl argo rollouts get rollout auth-service-rollout -n app --watch"
                                echo "  kubectl argo rollouts get rollout user-service-rollout -n app --watch"
                                echo ""
                                echo "분석 상태 확인:"
                                echo "  kubectl get analysisruns -n app"
                                echo "  kubectl get analysistemplates -n app"
                                ;;
                            production)
                                echo "=== 프로덕션 배포 (main 브랜치) ==="
                                cd aws/canary-deployment/services

                                # Ingress 배포
                                echo "Ingress 배포 중..."
                                if [ -f "../../eks-app/ingress/app-ingress.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f ../../eks-app/ingress/app-ingress.yaml -n app
                                fi

                                # 환경변수 치환
                                mkdir -p /tmp/k8s
                                substitute_vars auth-service/auth-service-rollout.yaml /tmp/k8s/auth-rollout.yaml
                                substitute_vars user-service/user-service-rollout.yaml /tmp/k8s/user-rollout.yaml

                                /usr/local/bin/kubectl apply -f auth-service/auth-service-services.yaml -n app
                                /usr/local/bin/kubectl apply -f /tmp/k8s/auth-rollout.yaml -n app
                                /usr/local/bin/kubectl apply -f user-service/user-service-services.yaml -n app
                                /usr/local/bin/kubectl apply -f /tmp/k8s/user-rollout.yaml -n app

                                echo "프로덕션 배포 완료 - 통합 ConfigMap/Secret 기반"
                                ;;
                            development|feature)
                                echo "=== ${DEPLOYMENT_STRATEGY} 배포 ==="
                                cd aws/canary-deployment/services

                                # Ingress 배포
                                if [ -f "../../eks-app/ingress/app-ingress.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f ../../eks-app/ingress/app-ingress.yaml -n app
                                fi

                                # 환경변수 치환
                                mkdir -p /tmp/k8s
                                substitute_vars auth-service/auth-service-rollout.yaml /tmp/k8s/auth-rollout.yaml
                                substitute_vars user-service/user-service-rollout.yaml /tmp/k8s/user-rollout.yaml

                                /usr/local/bin/kubectl apply -f auth-service/auth-service-services.yaml -n app
                                /usr/local/bin/kubectl apply -f /tmp/k8s/auth-rollout.yaml -n app
                                /usr/local/bin/kubectl apply -f user-service/user-service-services.yaml -n app
                                /usr/local/bin/kubectl apply -f /tmp/k8s/user-rollout.yaml -n app

                                echo "${DEPLOYMENT_STRATEGY} 배포 완료 - 통합 ConfigMap/Secret 기반"
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
                    
                    echo "통합 ConfigMap/Secret 상태:"
                    /usr/local/bin/kubectl get configmap,secret -n app | grep app- || true
                    echo ""
                    
                    echo "Redis 환경변수 확인:"
                    /usr/local/bin/kubectl get configmap app-config -n app -o yaml | grep -A 2 -B 2 REDIS || true
                    echo ""
                    
                    case "${DEPLOYMENT_STRATEGY}" in
                        canary)
                            echo "Analysis Templates 상태:"
                            /usr/local/bin/kubectl get analysistemplates -n app || true
                            echo ""
                            echo "Rollout 상태 (카나리 배포):"
                            /usr/local/bin/kubectl get rollouts -n app || true
                            echo ""
                            echo "Services 상태:"
                            /usr/local/bin/kubectl get services -n app || true
                            echo ""
                            echo "Pods 상태:"
                            /usr/local/bin/kubectl get pods -n app || true
                            echo ""
                            echo "Analysis Runs 상태:"
                            /usr/local/bin/kubectl get analysisruns -n app || true
                            ;;
                        *)
                            echo "전체 리소스 상태:"
                            /usr/local/bin/kubectl get all -n app || true
                            ;;
                    esac
                    
                    echo ""
                    echo "=== 배포 완료 정보 ==="
                    echo "브랜치: ${BRANCH_NAME_CLEAN}"
                    echo "배포 전략: ${DEPLOYMENT_STRATEGY}"
                    echo "이미지 태그: ${IMAGE_TAG}"
                    echo "네임스페이스: app (단일 네임스페이스)"
                    echo "환경변수 관리: Spring Cloud 제거된 통합 ConfigMap/Secret"
                    echo "카나리 분석: Analysis Templates 포함"
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
배포 정보:
- 브랜치: ${env.BRANCH_NAME_CLEAN}
- 이미지: canary 태그  
- 네임스페이스: app
- 환경변수: Spring Cloud 제거된 깔끔한 설정
- 카나리 분석: Analysis Templates 포함
- 단계: 10% → 25% → 50% → 100% 자동 진행 (분석 기반)

모니터링 명령어:
kubectl argo rollouts get rollout auth-service-rollout -n app --watch
kubectl argo rollouts get rollout user-service-rollout -n app --watch

분석 상태 확인:
kubectl get analysisruns -n app
kubectl get analysistemplates -n app
"""
                        break
                    case 'production':
                        message = """
프로덕션 배포 완료! (main 브랜치)
배포 정보:
- 브랜치: ${env.BRANCH_NAME_CLEAN}
- 이미지: latest, stable 태그
- 네임스페이스: app
- 환경변수: Spring Cloud 제거된 깔끔한 설정
"""
                        break
                    case 'development':
                        message = """
개발환경 배포 완료!
배포 정보:
- 브랜치: ${env.BRANCH_NAME_CLEAN}
- 이미지: dev 태그
- 네임스페이스: app
- 환경변수: Spring Cloud 제거된 깔끔한 설정
"""
                        break
                    default:
                        message = """
피처 빌드 완료!
배포 정보:
- 브랜치: ${env.BRANCH_NAME_CLEAN}
- 이미지: ${env.IMAGE_TAG}
- 네임스페이스: app
- 환경변수: Spring Cloud 제거된 깔끔한 설정
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
                    find ./auth-service/build -name "*.jar" 2>/dev/null || echo "   Auth Service JAR 없음"
                    find ./user-service/build -name "*.jar" 2>/dev/null || echo "   User Service JAR 없음"
                    
                    echo ""
                    echo "2. 최근 쿠버네티스 이벤트:"
                    /usr/local/bin/kubectl get events -n app --sort-by='.lastTimestamp' | tail -10 || echo "   이벤트 조회 실패"
                    
                    echo ""
                    echo "3. Analysis Templates 상태:"
                    /usr/local/bin/kubectl get analysistemplates -n app || echo "   Analysis Templates 없음"
                    
                    echo "4. 통합 ConfigMap/Secret 상태:"
                    /usr/local/bin/kubectl get configmap,secret -n app | grep app- || echo "   통합 ConfigMap/Secret 조회 실패"
                    
                    echo ""
                    echo "5. 현재 파일 구조 확인:"
                    echo "   AWS 디렉토리 구조:"
                    find aws/canary-deployment/services -name "*.yaml" -type f | head -15 || echo "   파일 구조 확인 실패"
                    
                    echo ""
                    echo "6. Docker 이미지 상태:"
                    docker images | grep -E "(auth-service|user-service)" | head -5 || echo "   Docker 이미지 없음"
                    
                    echo ""
                    echo "7. ECR 로그인 상태:"
                    /usr/local/bin/aws sts get-caller-identity || echo "   AWS 인증 실패"
                '''
            }
        }
        
        always {
            script {
                echo "=== 정리 작업 시작 ==="
                sh '''
                    echo "=== 빌드 통계 ==="
                    echo "빌드 번호: ${BUILD_NUMBER}"
                    echo "빌드 시간: $(date)"
                    echo "네임스페이스: app (단일 네임스페이스)"
                    echo "카나리 분석: Analysis Templates 포함"
                    echo "배포 전략: ${DEPLOYMENT_STRATEGY}"
                    
                    echo ""
                    echo "=== 최종 배포 상태 확인 ==="
                    echo "ConfigMap: $(/usr/local/bin/kubectl get configmap app-config -n app --no-headers 2>/dev/null | wc -l || echo '0')"
                    echo "Secret: $(/usr/local/bin/kubectl get secret app-secrets -n app --no-headers 2>/dev/null | wc -l || echo '0')"
                    echo "Analysis Templates: $(/usr/local/bin/kubectl get analysistemplates -n app --no-headers 2>/dev/null | wc -l || echo '0')"
                    echo "Rollouts: $(/usr/local/bin/kubectl get rollouts -n app --no-headers 2>/dev/null | wc -l || echo '0')"
                    echo "Pod 개수: $(/usr/local/bin/kubectl get pods -n app --no-headers 2>/dev/null | wc -l || echo '0')"
                    
                    echo ""
                    echo "=== 파일 구조 최종 확인 ==="
                    echo "사용된 YAML 파일들:"
                    case "${DEPLOYMENT_STRATEGY}" in
                        canary)
                            echo "Ingress: ../../eks-app/ingress/app-ingress.yaml"
                            echo "Auth Analysis: auth-service/auth-service-analysis.yaml"
                            echo "User Analysis: user-service/user-service-analysis.yaml" 
                            echo "Auth Rollout: auth-service/auth-service-rollout.yaml"
                            echo "User Rollout: user-service/user-service-rollout.yaml"
                            echo "Auth Services: auth-service/auth-service-services.yaml"
                            echo "User Services: user-service/user-service-services.yaml"
                            ;;
                        *)
                            echo "기본 배포 완료"
                            ;;
                    esac
                    
                    echo ""
                    echo "=== 정리 완료 ==="
                    echo "Spring Cloud 의존성 제거된 깔끔한 환경"
                    echo "카나리 분석 기능 포함"
                    echo "통합 ConfigMap/Secret 기반"
                    echo "파이프라인 종료: $(date)"
                '''
            }
        }
    }
}