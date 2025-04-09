# git-flow-basic

## 브랜치 목록
1. Production : 운영중인(배포된) 소스
2. Development : 개발 소스
3. QA : 개발 테스트용 소스 (dev)
4. Release : 통합 QA용 소스 (staging)
6. Feature : 개별 작업 브랜치
7. Hotfix : 버그 픽스 브랜치

## 작업 브랜치 네이밍 규칙
[Feature / Hotfix] / [모듈] / [개발자별 Prefix]-[이슈ID]-([기능])
   - Feature / common / jwkoo-TQ-123-token (공통 모듈 작업)
   - Hotfix / product / jwkoo-TQ-125-wishlist (상품 모듈 버그 픽스)

## 일반적인 기능개발 시나리오
1. Development -> Feature Checkout
   - 모든 개발자는 Development에서 작업브랜치를 딴다.
3. 기능개발
4. 개발자 테스트
5. Feature -> QA (기능 테스트)
7. Feature -> Development
   - 해당 Feature 브랜치 삭제(작업 완료. 이후 작업은 버그 수정 작업(Hotfix)으로 생각함.)
9. Development -> Release (통합 QA)
10. Release -> Production (Tag 작성 및 배포)
11. Production -> QA, Development, Release (소스 현행화)
12. Hotfix 브랜치 삭제 (수정 완료. 배포 이후에 버그는 새로운 버그로 인식함.)

## 버그 픽스
1. 기능 테스트 시점까지 발견된 버그는 해당 작업브랜치에서 수정후 다시 병합 (Feature -> QA)
2. 기능 테스트 이후부터 배포 전까지 발견된 버그는 Release -> Hotfix 로 체크아웃 이후 해결한다. (Hotfix -> Release)
3. 운영중 발생한 버그는 롤백 후 Production -> Hotfix로 체크아웃 이후 해결한다.
   - 긴급도 하 (일반 기능 이슈) : 해결된 이후 모든 테스트 절차를 거쳐 다시 배포한다. (Release -> Production)
   - 긴급도 상 (전체 로그인 장애, 결제 장애 등) : 기능 테스트 이후 운영배포한다. 그 이후 소스 현행화 진행한다. (Hotfix -> Production)

## 병합 규칙
1. 모든 병합은 반드시 PR을 통해서 진행한다.
2. CI 통과는 필수이다.
3. 리뷰어 지정은 필수이다.
4. 최소 1명 이상의 승인이 필수이다.
5. Rebase는 개인작업중 커밋을 정리한다거나, Feature 브랜치를 최신화 할때 말고는 사용하지 않음.

## 병합 방식
1. Feature -> QA             : Squash merge       -> 커밋 단순화, 기능단위 정리
2. Feature -> Development    : Squash merge       -> 커밋 단순화, 기능단위 정리
3. Development -> Release    : Merge commit       -> QA시 변경점 추적 용이
4. Release -> Production     : Merge commit       -> 배포 이력 보존
5. Hotfix -> Release         : Squash 또는 Merge   -> 상황에 따라서 선택
6. Hotfix -> Production      : Squash 또는 Merge   -> 상황에 따라서 선택

## 브랜치 보호 규칙
공통 브랜치(Production, Release, QA, Development)는 보호규칙을 설정한다.
1. Force push 금지.
2. PR 리뷰 1건 이상 필요.
3. PR전 머지 충돌이 없어야 함.

## 충돌 시나리오 대처 방안 마련
추후 추가

## 자동화 스크립트 적용 (스프링부트 기준)

1. 프로젝트를 내려 받은 후 ./gradlew build 실행해줘야 로컬 hook 적용됨.
2. 결국 .git/hooks 디렉토리 아래 hooks 디렉토리에 있는 파일들이 복사 되어야 한다.
### build.gradle.kts

<pre lang="markdown">
```kotlin
val installGitHooks by tasks.registering(Copy::class) {
    val gitHookDir = file(".git/hooks")
    val customHookDir = file("hooks")

    // 언제나 복사 (기존 파일이 있어도 최신 내용으로 덮어쓰기)
    from(customHookDir)
    into(gitHookDir)

    // 기존 파일과 동일하면 overwrite 안 함, 달라지면 덮어씀
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    doLast {
        println("✅ Git hooks copied from 'hooks/' to '.git/hooks'")
        fileTree(gitHookDir).forEach {
            it.setExecutable(true)
        }
    }
}

// 주요 task 실행 시 항상 최신 hook 유지
listOf("build", "test", "bootRun").forEach { taskName ->
    tasks.named(taskName).configure {
        dependsOn(installGitHooks)
    }
}
```
</pre>

### hooks/commit-msg.sh
<pre lang="markdown">
```shell
#!/bin/bash

COMMIT_MSG_FILE=$1
COMMIT_MSG=$(cat "$COMMIT_MSG_FILE")
BRANCH_NAME=$(git symbolic-ref --short HEAD)

# 커밋 메시지가 이미 prefix (예: feat:) 있으면 패스
if [[ "$COMMIT_MSG" =~ ^(feat|fix|docs|style|refactor|test|chore): ]]; then
  exit 0
fi

# 브랜치 이름 기반 prefix 결정
if [[ "$BRANCH_NAME" == Feature/* ]]; then
  PREFIX="feat"
elif [[ "$BRANCH_NAME" == Fix/* || "$BRANCH_NAME" == Hotfix/* ]]; then
  PREFIX="fix"
elif [[ "$BRANCH_NAME" == Docs/* ]]; then
  PREFIX="docs"
elif [[ "$BRANCH_NAME" == Refactor/* ]]; then
  PREFIX="refactor"
else
  PREFIX="chore"
fi

# 커밋 메시지에 prefix 추가
echo "${PREFIX}: $COMMIT_MSG" > "$COMMIT_MSG_FILE"
echo "ℹ️ 커밋 메시지에 자동 prefix 추가됨: ${PREFIX}"
```
</pre>

### hooks/pre-push.sh
<pre lang="markdown">
```shell
#!/bin/bash

# 현재 브랜치 이름
BRANCH_NAME=$(git symbolic-ref --short HEAD)

# 브랜치 네이밍 규칙 정규식
REGEX="^(Production|Development|Feature|Hotfix|Release)(/[a-z0-9\-]+)?$"

# 브랜치 이름 규칙 검사
if ! [[ "$BRANCH_NAME" =~ $REGEX ]]; then
  echo "❌ 브랜치 이름이 규칙에 맞지 않습니다: $BRANCH_NAME"
  echo "👉 올바른 예: Feature/login-form, Hotfix/fix-bug"
  exit 1
fi

# === Feature 브랜치 검사 ===
if [[ "$BRANCH_NAME" == Feature/* ]]; then
  BASE_BRANCH="Development"
  BASE_COMMIT=$(git merge-base "$BRANCH_NAME" "$BASE_BRANCH" 2>/dev/null)
  BASE_HEAD=$(git rev-parse "$BASE_BRANCH" 2>/dev/null)

  if [[ "$BASE_COMMIT" != "$BASE_HEAD" ]]; then
    echo "❌ [BLOCKED] Feature 브랜치는 Development 브랜치에서 파생되어야 합니다."
    echo "👉 현재 브랜치: $BRANCH_NAME"
    exit 1
  fi
fi

# === Hotfix 브랜치 검사 ===
if [[ "$BRANCH_NAME" == Hotfix/* ]]; then
  BASE_RELEASE=$(git merge-base "$BRANCH_NAME" Release 2>/dev/null || true)
  BASE_PROD=$(git merge-base "$BRANCH_NAME" Production 2>/dev/null || true)

  HEAD_RELEASE=$(git rev-parse Release 2>/dev/null || echo "")
  HEAD_PROD=$(git rev-parse Production 2>/dev/null || echo "")

  if [[ "$BASE_RELEASE" != "$HEAD_RELEASE" && "$BASE_PROD" != "$HEAD_PROD" ]]; then
    echo "❌ [BLOCKED] Hotfix 브랜치는 Release 또는 Production 브랜치에서 파생되어야 합니다."
    echo "👉 현재 브랜치: $BRANCH_NAME"
    exit 1
  fi
fi

# === 모든 조건 통과 시 푸시 허용 ===
exit 0
```
</pre>

1. .github/workflows 이 디렉토리 아래에 있어야 github Action 으로 등록되고 사용할수 있음.
2. 중요한것은 1번이라도 성공해야 설정에 등록할수 있음..
3. 아래 이미지에 밑줄 친곳을 잘 확인하고 등록해야 함..

### .github/workflows/flow.yml
<pre lang="markdown">
```yml
name: flow

on:
  pull_request:
    branches:
      - Production

jobs:
  check-source:
    name: flow
    runs-on: ubuntu-latest
    steps:
      - name: Fail if not from release/* or hotfix/*
        run: |
          echo "🔍 PR Source Branch: ${{ github.head_ref }}"
          
          BRANCH="${{ github.head_ref }}"
          
          if [[ ! "$BRANCH" =~ ^(Release$|Hotfix/.*) ]]; then
            echo "❌ Only Release/* or Hotfix/* branches can merge into production."
            exit 1
          fi
```
</pre>

![image](https://github.com/user-attachments/assets/6ee85c4d-8d2b-466a-806c-61dbaf5ffd4a)

