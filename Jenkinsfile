pipeline {
    agent any
    
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
                    // 브랜치명 정리 - cloud, main 전략에 맞게 수정
                    def branchNameClean = sh(
                        script: '''
                            echo "DEBUG - BRANCH_NAME: ${BRANCH_NAME:-empty}" >&2
                            echo "DEBUG - GIT_BRANCH: ${GIT_BRANCH:-empty}" >&2
                            echo "DEBUG - CHANGE_BRANCH: ${CHANGE_BRANCH:-empty}" >&2

                            if [ -n "${CHANGE_BRANCH:-}" ]; then
                                CURRENT_BRANCH="${CHANGE_BRANCH}"
                            elif [ -n "${GIT_BRANCH:-}" ]; then
                                CURRENT_BRANCH="${GIT_BRANCH}"
                            elif [ -n "${BRANCH_NAME:-}" ]; then
                                CURRENT_BRANCH="${BRANCH_NAME}"
                            else
                                CURRENT_BRANCH=$(git symbolic-ref --short HEAD 2>/dev/null || echo "detached-$(git rev-parse --short HEAD)")
                            fi

                            # origin/ 프리픽스 제거 및 표준화
                            CLEAN_BRANCH=$(echo "$CURRENT_BRANCH" | sed 's|^origin/||' | sed 's|^refs/heads/||')

                            # 브랜치명 표준화 - cloud, main만 주로 사용
                            case "$CLEAN_BRANCH" in
                                main|master)
                                    echo "main"
                                    ;;
                                cloud|cloud-deploy)
                                    echo "cloud"
                                    ;;
                                dev|develop|development)
                                    echo "dev"
                                    ;;
                                *)
                                    # feature 브랜치는 cloud 기반으로 처리
                                    echo "feature-$(echo $CLEAN_BRANCH | sed 's/[^a-zA-Z0-9]/-/g')"
                                    ;;
                            esac
                        ''',
                        returnStdout: true
                    ).trim()

                    // 이미지 태그 생성 전략 개선
                    def imageTag
                    def deploymentStrategy

                    switch(branchNameClean) {
                        case 'main':
                            imageTag = "v${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                            deploymentStrategy = "production"
                            break
                        case 'cloud':
                            imageTag = "canary-${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                            deploymentStrategy = "canary"
                            break
                        case 'dev':
                            imageTag = "dev-${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                            deploymentStrategy = "development"
                            break
                        default:
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
                        export ECR_REGISTRY=${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
                        
                        /usr/local/bin/aws ecr get-login-password --region $AWS_REGION | \
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
                        export ECR_REGISTRY=${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
                        
                        echo "=== 빌드 정보 ==="
                        echo "브랜치: ${BRANCH_NAME_CLEAN}"
                        echo "배포 전략: ${DEPLOYMENT_STRATEGY}"
                        echo "이미지 태그: ${IMAGE_TAG}"

                        chmod +x ./gradlew
                        ./gradlew clean --no-daemon
                        
                        # 브랜치별 태그 전략 함수
                        apply_deployment_tags() {
                            local service_name="$1"
                            local image_path="${ECR_REGISTRY}/${ECR_PREFIX}/${service_name}"
                            
                            echo "=== ${service_name} 태그 적용 ==="

                            case "${DEPLOYMENT_STRATEGY}" in
                                production)
                                    echo "프로덕션 배포 - latest, stable 태그 추가"
                                    docker tag ${image_path}:${IMAGE_TAG} ${image_path}:latest
                                    docker tag ${image_path}:${IMAGE_TAG} ${image_path}:stable
                                    docker push ${image_path}:latest
                                    docker push ${image_path}:stable
                                    ;;
                                canary)
                                    echo "카나리 배포 - canary 태그 추가"
                                    docker tag ${image_path}:${IMAGE_TAG} ${image_path}:canary
                                    docker push ${image_path}:canary
                                    ;;
                                development)
                                    echo "개발 배포 - dev 태그 추가"
                                    docker tag ${image_path}:${IMAGE_TAG} ${image_path}:dev
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
                        docker build -f ./auth-service/Dockerfile \
                            -t ${ECR_REGISTRY}/${ECR_PREFIX}/auth-service:${IMAGE_TAG} .
                        docker push ${ECR_REGISTRY}/${ECR_PREFIX}/auth-service:${IMAGE_TAG}
                        apply_deployment_tags "auth-service"

                        # User Service 빌드
                        echo "=== User Service 빌드 ==="
                        ./gradlew :user-service:build -x test --no-daemon
                        docker build -f ./user-service/Dockerfile \
                            -t ${ECR_REGISTRY}/${ECR_PREFIX}/user-service:${IMAGE_TAG} .
                        docker push ${ECR_REGISTRY}/${ECR_PREFIX}/user-service:${IMAGE_TAG}
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
                        export ECR_REGISTRY=${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
                        
                        echo "=== 배포 전략: ${DEPLOYMENT_STRATEGY} ==="

                        # 네임스페이스 생성
                        case "${DEPLOYMENT_STRATEGY}" in
                            production)
                                NAMESPACE="app"
                                echo "프로덕션 네임스페이스: ${NAMESPACE}"
                                ;;
                            canary)
                                NAMESPACE="app"
                                echo "카나리 네임스페이스: ${NAMESPACE}"
                                ;;
                            development)
                                NAMESPACE="app-dev"
                                echo "개발 네임스페이스: ${NAMESPACE}"
                                ;;
                            feature)
                                NAMESPACE="app-feature"
                                echo "피처 네임스페이스: ${NAMESPACE}"
                                ;;
                        esac

                        /usr/local/bin/kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | \
                            /usr/local/bin/kubectl apply -f -
                        
                        # sed를 사용한 환경변수 치환 함수 (카나리 이미지 태그 수정 포함)
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
                            echo "사용할 이미지 태그: ${ACTUAL_IMAGE_TAG}"
                            
                            sed -e "s|\${ECR_REGISTRY}|${ECR_REGISTRY}|g" \
                                -e "s|\${ECR_PREFIX}|${ECR_PREFIX}|g" \
                                -e "s|\${IMAGE_TAG}|${ACTUAL_IMAGE_TAG}|g" \
                                -e "s|\${DEPLOYMENT_STRATEGY}|${DEPLOYMENT_STRATEGY}|g" \
                                -e "s|\${AWS_REGION}|${AWS_REGION}|g" \
                                -e "s|\${AWS_ACCOUNT_ID}|${AWS_ACCOUNT_ID}|g" \
                                -e "s|\${DB_URL}|${DB_URL}|g" \
                                -e "s|\${DB_USERNAME}|${DB_USERNAME}|g" \
                                -e "s|\${DB_PASSWORD}|${DB_PASSWORD}|g" \
                                -e "s|\${JWT_SECRET}|${JWT_SECRET}|g" \
                                -e "s|\${JWT_REFRESH_SECRET}|${JWT_REFRESH_SECRET}|g" \
                                -e "s|\${REDIS_HOST}|${REDIS_HOST}|g" \
                                -e "s|\${REDIS_PORT}|${REDIS_PORT}|g" \
                                -e "s|\${MAIL_USERNAME}|${MAIL_USERNAME}|g" \
                                -e "s|\${MAIL_PASSWORD}|${MAIL_PASSWORD}|g" \
                                -e "s|\${GOOGLE_CLIENT_ID}|${GOOGLE_CLIENT_ID}|g" \
                                -e "s|\${GOOGLE_CLIENT_SECRET_ID}|${GOOGLE_CLIENT_SECRET_ID}|g" \
                                -e "s|\${AUTH_SERVICE_PASSPORT_SECRET}|${AUTH_SERVICE_PASSPORT_SECRET}|g" \
                                -e "s|\${USER_SERVICE_PASSPORT_SECRET}|${USER_SERVICE_PASSPORT_SECRET}|g" \
                                "$input_file" > "$output_file"

                            echo "치환 완료: $(wc -l < "$output_file") 라인"
                            
                            # 치환되지 않은 변수 확인
                            REMAINING_VARS=$(grep -o '\${[^}]*}' "$output_file" || true)
                            if [ -n "$REMAINING_VARS" ]; then
                                echo "WARNING: 치환되지 않은 변수들: $REMAINING_VARS"
                            else
                                echo "SUCCESS: 모든 환경변수 치환 완료"
                            fi
                            
                            # 이미지 태그 확인
                            echo "최종 이미지 확인:"
                            grep "image:" "$output_file" | head -2
                        }

                        # 배포 전략별 처리
                        case "${DEPLOYMENT_STRATEGY}" in
                            canary)
                                echo "=== 카나리 배포 실행 (cloud 브랜치) ==="
                                cd aws/canary-deployment/services

                                # 1. Ingress 먼저 배포 (트래픽 라우팅 기반)
                                echo "1. Ingress 배포 중..."
                                if [ -f "../eks-app/ingress/app-ingress.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f ../eks-app/ingress/app-ingress.yaml -n ${NAMESPACE}
                                    echo "Ingress 배포 완료"
                                else
                                    echo "WARNING: Ingress 파일을 찾을 수 없습니다"
                                fi

                                # 2. 기존 Deployment 삭제 (Rollout으로 교체)
                                echo "2. 기존 Deployment 확인 및 삭제..."
                                /usr/local/bin/kubectl delete deployment auth-deployment -n ${NAMESPACE} --ignore-not-found
                                /usr/local/bin/kubectl delete deployment user-deployment -n ${NAMESPACE} --ignore-not-found
                                
                                # 3. 환경변수 치환하여 임시 파일 생성
                                echo "3. 환경변수 치환..."
                                mkdir -p /tmp/k8s
                                substitute_vars auth-service/auth-service-rollout.yaml /tmp/k8s/auth-rollout.yaml
                                substitute_vars user-service/user-service-rollout.yaml /tmp/k8s/user-rollout.yaml

                                # 4. Auth Service 카나리 배포
                                echo "4. Auth Service 카나리 배포..."
                                /usr/local/bin/kubectl apply -f auth-service/auth-service-analysis.yaml -n ${NAMESPACE}
                                /usr/local/bin/kubectl apply -f auth-service/auth-service-services.yaml -n ${NAMESPACE}
                                /usr/local/bin/kubectl apply -f /tmp/k8s/auth-rollout.yaml -n ${NAMESPACE}

                                # 5. User Service 카나리 배포  
                                echo "5. User Service 카나리 배포..."
                                /usr/local/bin/kubectl apply -f user-service/user-service-analysis.yaml -n ${NAMESPACE}
                                /usr/local/bin/kubectl apply -f user-service/user-service-services.yaml -n ${NAMESPACE}
                                /usr/local/bin/kubectl apply -f /tmp/k8s/user-rollout.yaml -n ${NAMESPACE}

                                echo "카나리 배포 시작됨 - Argo Rollouts에서 자동 진행"
                                echo "모니터링 명령어:"
                                echo "  kubectl argo rollouts get rollout auth-service-rollout -n ${NAMESPACE} --watch"
                                echo "  kubectl argo rollouts get rollout user-service-rollout -n ${NAMESPACE} --watch"
                                echo "수동 진행 명령어:"
                                echo "  kubectl argo rollouts promote auth-service-rollout -n ${NAMESPACE}"
                                echo "  kubectl argo rollouts promote user-service-rollout -n ${NAMESPACE}"
                                ;;
                            production)
                                echo "=== 프로덕션 배포 (main 브랜치) ==="
                                cd aws/canary-deployment/services

                                # Ingress 배포
                                echo "Ingress 배포 중..."
                                if [ -f "../eks-app/ingress/app-ingress.yaml" ]; then
                                    /usr/local/bin/kubectl apply -f ../eks-app/ingress/app-ingress.yaml -n ${NAMESPACE}
                                fi

                                # 프로덕션도 Rollouts 사용하여 안전한 배포
                                mkdir -p /tmp/k8s
                                substitute_vars auth-service/auth-service-rollout.yaml /tmp/k8s/auth-rollout.yaml
                                substitute_vars user-service/user-service-rollout.yaml /tmp/k8s/user-rollout.yaml

                                /usr/local/bin/kubectl apply -f auth-service/auth-service-services.yaml -n ${NAMESPACE}
                                /usr/local/bin/kubectl apply -f /tmp/k8s/auth-rollout.yaml -n ${NAMESPACE}
                                /usr/local/bin/kubectl apply -f user-service/user-service-services.yaml -n ${NAMESPACE}
                                /usr/local/bin/kubectl apply -f /tmp/k8s/user-rollout.yaml -n ${NAMESPACE}

                                echo "프로덕션 안전 배포 완료"
                                ;;
                            development)
                                echo "=== 개발환경 배포 ==="
                                cd aws/canary-deployment/services
                                
                                # 기본 서비스들 먼저 배포 (basic-services 활용)
                                /usr/local/bin/kubectl apply -f basic-services/auth-service.yaml -n ${NAMESPACE}
                                /usr/local/bin/kubectl apply -f basic-services/user-service.yaml -n ${NAMESPACE}
                                
                                # 개발환경용 간단한 Deployment 사용 (있다면)
                                if [ -f "basic-services/auth-deployment-dev.yaml" ]; then
                                    echo "개발환경용 Deployment 사용"
                                    mkdir -p /tmp/k8s
                                    substitute_vars basic-services/auth-deployment-dev.yaml /tmp/k8s/auth-deployment-dev.yaml
                                    substitute_vars basic-services/user-deployment-dev.yaml /tmp/k8s/user-deployment-dev.yaml
                                    
                                    /usr/local/bin/kubectl apply -f /tmp/k8s/auth-deployment-dev.yaml -n ${NAMESPACE}
                                    /usr/local/bin/kubectl apply -f /tmp/k8s/user-deployment-dev.yaml -n ${NAMESPACE}
                                else
                                    echo "개발환경도 Rollout 사용"
                                    mkdir -p /tmp/k8s
                                    substitute_vars auth-service/auth-service-rollout.yaml /tmp/k8s/auth-rollout.yaml
                                    substitute_vars user-service/user-service-rollout.yaml /tmp/k8s/user-rollout.yaml
                                    
                                    /usr/local/bin/kubectl apply -f auth-service/auth-service-services.yaml -n ${NAMESPACE}
                                    /usr/local/bin/kubectl apply -f /tmp/k8s/auth-rollout.yaml -n ${NAMESPACE}
                                    /usr/local/bin/kubectl apply -f user-service/user-service-services.yaml -n ${NAMESPACE}
                                    /usr/local/bin/kubectl apply -f /tmp/k8s/user-rollout.yaml -n ${NAMESPACE}
                                fi
                                
                                echo "개발환경 배포 완료"
                                ;;
                            feature)
                                echo "=== 피처 브랜치 배포 ==="
                                echo "피처 테스트용 배포 준비"
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
                    case "${DEPLOYMENT_STRATEGY}" in
                        canary)
                            echo "=== 카나리 배포 상태 확인 (cloud 브랜치) ==="
                            /usr/local/bin/kubectl get rollouts -n app || true
                            echo ""
                            echo "Auth Service Rollout 상태:"
                            /usr/local/bin/kubectl argo rollouts get rollout auth-service-rollout -n app || true
                            echo ""
                            echo "User Service Rollout 상태:"
                            /usr/local/bin/kubectl argo rollouts get rollout user-service-rollout -n app || true
                            echo ""
                            echo "Ingress 상태:"
                            /usr/local/bin/kubectl get ingress -n app || true
                            ;;
                        production)
                            echo "=== 프로덕션 배포 상태 확인 (main 브랜치) ==="
                            /usr/local/bin/kubectl get all -n app || true
                            /usr/local/bin/kubectl get ingress -n app || true
                            ;;
                        development)
                            echo "=== 개발환경 상태 확인 ==="
                            /usr/local/bin/kubectl get all -n app-dev || true
                            ;;
                        feature)
                            echo "=== 피처 환경 상태 확인 ==="
                            /usr/local/bin/kubectl get all -n app-feature || true
                            ;;
                    esac
                    
                    echo ""
                    echo "=== 배포 완료 정보 ==="
                    echo "브랜치: ${BRANCH_NAME_CLEAN}"
                    echo "배포 전략: ${DEPLOYMENT_STRATEGY}"
                    echo "이미지 태그: ${IMAGE_TAG}"
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
- 안정적인 프로덕션 서비스 배포됨
"""
                        break
                    case 'development':
                        message = """
개발환경 배포 완료!
- 브랜치: ${env.BRANCH_NAME_CLEAN}
- 이미지: dev 태그
- 개발 테스트 준비 완료
"""
                        break
                    default:
                        message = """
피처 빌드 완료!
- 브랜치: ${env.BRANCH_NAME_CLEAN}
- 이미지: ${env.IMAGE_TAG}
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
                    echo "2. Gradle 빌드 로그 확인:"
                    if [ -f "./build/reports/problems/problems-report.html" ]; then
                        echo "   빌드 문제 리포트 생성됨"
                    else
                        echo "   빌드 문제 리포트 없음"
                    fi
                    
                    echo ""
                    echo "3. 최근 쿠버네티스 이벤트:"
                    /usr/local/bin/kubectl get events -n app --sort-by='.lastTimestamp' | tail -5 || echo "   이벤트 조회 실패"
                    
                    echo ""
                    echo "4. Docker 이미지 상태:"
                    docker images | grep -E "(auth-service|user-service)" | head -10 || echo "   Docker 이미지 없음"
                    
                    echo ""
                    echo "=== 해결 방법 제안 ==="
                    echo "1. 의존성 문제인 경우: ./gradlew clean 후 재시도"
                    echo "2. Docker 문제인 경우: Docker 데몬 재시작"
                    echo "3. 개별 서비스 빌드 테스트: ./gradlew :auth-service:build -x test"
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
                    echo "정리 완료"
                    echo "파이프라인 종료: $(date)"
                '''
            }
        }
    }
}

