# BankCore 제출 전 체크리스트

## 현재 결론

BankCore는 포트폴리오 제출 가능한 백엔드 MVP와 로컬 검증용 프론트엔드 Lab Console을 갖춘 상태입니다.

이 프로젝트는 실제 은행 코어뱅킹 전체가 아니라, 금융 이체 정확성에서 중요한 트랜잭션, 멱등성, 동시성 제어, 정산 검증, SQL pagination 근거를 작게 재현하고 증명하는 프로젝트입니다.

## 제출 전 실행 확인

아래 명령이 성공하면 로컬 실행과 테스트 기준은 충족됩니다.

```bash
cd /Users/chan12/Desktop/Java/bankcore
docker compose up -d
./gradlew test --no-daemon
./scripts/demo.sh
cd frontend
npm install
npm run lint
npm run build
```

기대 결과:

- Gradle test: `BUILD SUCCESSFUL`
- Demo script: `Demo completed successfully.`
- Reconciliation response: `[]`
- Frontend lint: 오류 없음
- Frontend build: Vite production build 성공

## 리뷰어에게 먼저 보여줄 파일

- `README.md`: 실행법, demo script, Swagger UI, API 사용 흐름
- `frontend/README.md`: Lab Console 실행법과 검증 흐름
- `docs/status.md`: 현재 구현 완료 목록
- `docs/evidence/2026-09-05-hardening.md`: 최종 hardening 근거
- `docs/evidence/2026-09-05-frontend-lab-console.md`: 프론트엔드 Lab Console 검증 근거
- `docs/evidence/2026-09-04-core-behavior.md`: 핵심 동작 검증 근거
- `docs/evidence/2026-09-04-journal-pagination-benchmark.md`: keyset pagination SQL 근거
- `docs/portfolio-writeup-ko.md`: 포트폴리오 설명문
- `docs/resume-and-interview-notes-ko.md`: 이력서/면접 답변용 요약
- `docs/adr/`: 설계 결정 기록

## 면접에서 안전하게 말할 수 있는 주장

- 공개 입금/출금 API를 의도적으로 제외하고, public money movement를 idempotency header가 필수인 internal transfer로 제한했습니다.
- 이체 성공 시 `financial_transaction` 1건과 `account_journal_entry` 2건이 함께 기록됩니다.
- 출금 직후 또는 journal flush 이후 예외가 발생해도 잔액, 거래, journal, idempotency row가 함께 rollback됩니다.
- 같은 idempotency key와 같은 request fingerprint는 같은 결과를 replay하고, 다른 fingerprint 재사용은 conflict로 거부합니다.
- 동시 same-key 요청 50개가 들어와도 money effect는 한 번만 발생하는 것을 Testcontainers MySQL 통합 테스트로 검증했습니다.
- 동시 same-key different-fingerprint 요청은 하나만 성공하고 하나는 conflict가 되는 것을 검증했습니다.
- DB integrity 예외는 기대한 unique constraint 이름이 확인될 때만 business exception으로 변환합니다.
- no-lock, optimistic lock, pessimistic lock 실험을 통해 동시성 실패와 방어 전략을 비교했습니다.
- 실제 internal transfer는 두 계좌를 account id 순서로 pessimistic write lock 하여 반대 방향 이체 deadlock 위험을 줄였습니다.
- stored balance와 journal-derived balance를 비교하는 reconciliation API로 불일치를 탐지합니다.
- account journal 조회는 `(account_id, id)` 인덱스와 keyset pagination으로 구현했고, 50,000건 synthetic benchmark evidence를 남겼습니다.
- OpenAPI JSON과 Swagger UI를 제공하고, core API path가 문서화되는지 테스트합니다.
- React/TypeScript Lab Console로 demo account, idempotent transfer, replay, conflict, journal, reconciliation 흐름을 한 화면에서 시연할 수 있습니다.

## 과장하면 안 되는 주장

- 실제 은행 코어뱅킹을 구현했다고 말하지 않습니다.
- 실제 고객 계좌 보호, 인증/인가, 규제 준수, 감사 불변성, AML/KYC/FDS를 구현했다고 말하지 않습니다.
- 완전한 복식부기 원장이나 결제망 연동을 구현했다고 말하지 않습니다.
- 운영 환경 보안 설정을 완성했다고 말하지 않습니다.

## 남겨둔 확장 과제

- production-style scope로 확장할 경우 authentication/authorization 추가
- optimistic lock conflict에 대한 bounded retry 정책 추가
- 운영용 secrets 분리와 profile별 설정 강화
- OpenAPI annotation 세부 보강
- Lab Console 시연 영상 또는 스크린샷 추가

현재 포트폴리오 목적에서는 위 확장 과제들을 모두 구현하기보다, 범위 밖이라고 명확히 설명하는 편이 더 안전합니다.
