#!/bin/bash

# Git 브랜치 전략 설정 및 Jenkins 연동 스크립트
# main, dev, canary 브랜치 구조 생성

set -e

echo "=== Git 브랜치 전략 설정 ==="

# 현재 브랜치 확인
CURRENT_BRANCH=$(git branch --show-current)
echo "현재 브랜치: $CURRENT_BRANCH"

# 기본 브랜치들이 존재하는지 확인하고 생성
setup_branch() {
    local branch_name=$1
    local description=$2

    if git show-ref --verify --quiet refs/heads/$branch_name; then
        echo "✅ $branch_name 브랜치 이미 존재"
    else
        echo "🔧 $branch_name 브랜치 생성 중..."
        git checkout -b $branch_name
        git push -u origin $branch_name
        echo "✅ $branch_name 브랜치 생성 완료 - $description"
    fi
}

# 브랜치 보호 정책 설정 함수 (GitHub/GitLab 용)
setup_branch_protection() {
    echo ""
    echo "=== 브랜치 보호 정책 권장 사항 ==="
    echo ""
    echo "🔒 main 브랜치:"
    echo "   - PR/MR 필수"
    echo "   - 코드 리뷰 2명 이상"
    echo "   - CI/CD 성공 필수"
    echo "   - 관리자만 직접 푸시 허용"
    echo ""
    echo "🔧 dev 브랜치:"
    echo "   - PR/MR 필수"
    echo "   - 코드 리뷰 1명 이상"
    echo "   - CI 성공 필수"
    echo ""
    echo "🐦 canary 브랜치:"
    echo "   - main에서만 머지 허용"
    echo "   - 자동 카나리 배포 트리거"
    echo "   - 프로덕션 테스트 완료 후 main 머지"
}

# Jenkins 파이프라인 설정 정보
setup_jenkins_info() {
    echo ""
    echo "=== Jenkins 파이프라인 설정 ==="
    echo ""
    echo "🔥 각 브랜치별 자동 트리거:"
    echo ""
    echo "📦 main 브랜치:"
    echo "   - 프로덕션 배포 (latest, stable 태그)"
    echo "   - 완전한 테스트 수트 실행"
    echo "   - 보안 스캔 및 성능 테스트"
    echo ""
    echo "🔧 dev 브랜치:"
    echo "   - 개발환경 배포 (dev 태그)"
    echo "   - 빠른 테스트 및 통합 테스트"
    echo "   - 개발자 피드백용"
    echo ""
    echo "🐦 canary 브랜치:"
    echo "   - 카나리 배포 (canary 태그)"
    echo "   - 단계적 트래픽 증가 (10% → 25% → 50% → 100%)"
    echo "   - 자동 롤백 모니터링"
    echo ""
    echo "🚧 feature/* 브랜치:"
    echo "   - 빌드 검증만"
    echo "   - 임시 테스트 환경 (선택적)"
}

# 메인 설정 실행
echo "브랜치 전략 설정을 시작합니다..."
echo ""

# 기본 브랜치들 설정
setup_branch "main" "프로덕션 메인 브랜치"
setup_branch "dev" "개발 통합 브랜치"
setup_branch "canary" "카나리 배포 브랜치"

# 현재 브랜치로 돌아가기
git checkout $CURRENT_BRANCH

# 브랜치 구조 확인
echo ""
echo "=== 현재 브랜치 구조 ==="
git branch -a

# Git Flow 워크플로우 설명
echo ""
echo "=== 브랜치 전략 워크플로우 ==="
echo ""
echo "🔄 개발 흐름:"
echo "1. feature/* → dev (개발 및 테스트)"
echo "2. dev → canary (카나리 배포 검증)"
echo "3. canary → main (프로덕션 배포)"
echo ""
echo "🚨 핫픽스 흐름:"
echo "1. hotfix/* → main (긴급 수정)"
echo "2. main → dev (변경사항 동기화)"
echo "3. main → canary (다음 카나리에 반영)"

# Jenkins 및 보호 정책 정보 출력
setup_branch_protection
setup_jenkins_info

echo ""
echo "=== 다음 단계 ==="
echo "1. Jenkins에서 멀티브랜치 파이프라인 설정"
echo "2. 브랜치 보호 정책 적용 (GitHub/GitLab)"
echo "3. 각 브랜치별 배포 테스트"
echo ""
echo "✅ 브랜치 전략 설정 완료!"
