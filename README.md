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
