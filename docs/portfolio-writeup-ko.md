# BankCore 포트폴리오 설명문

## 한 줄 소개

BankCore는 Java/Spring Boot와 MySQL InnoDB를 사용해 금융 이체에서 중요한 트랜잭션 원자성, 멱등성, 동시성 제어, 정산 검증을 실험하고 증명하며, React Lab Console로 그 증거 흐름을 시연할 수 있게 만든 프로젝트입니다.

## 문제 정의

금융성 시스템에서 단순히 잔액을 더하고 빼는 기능만 구현하면 실제 장애 상황을 설명하기 어렵습니다. 네트워크 재시도, 서버 예외, 동시 요청, 부분 기록, 잘못된 재처리 같은 상황에서 잔액과 거래 기록이 어떻게 보존되는지를 증명해야 합니다.

이 프로젝트는 실제 은행 코어뱅킹 전체를 구현한다고 주장하지 않습니다. 대신 이체 정확성을 중심으로 범위를 줄이고, 작은 코드베이스 안에서 재현 가능한 실패 사례와 해결 증거를 남기는 것을 목표로 했습니다.

추가로 리뷰어가 curl 명령을 직접 따라 치지 않아도 핵심 흐름을 볼 수 있도록 React/TypeScript 기반 Lab Console을 만들었습니다. 이 화면은 고객용 인터넷뱅킹이 아니라, demo account, 이체, 멱등 replay, conflict, journal, reconciliation을 한 화면에서 확인하는 검증 콘솔입니다.

## 핵심 설계

- 계좌 생성 시 잔액은 항상 0에서 시작합니다.
- 공개 입금/출금 API는 제공하지 않고, 공개 금액 이동은 내부이체 API로 제한했습니다.
- 내부이체 API는 `X-Caller-Scope`와 `Idempotency-Key`를 필수로 요구합니다.
- 이체 성공 시 `financial_transaction` 1건과 `account_journal_entry` 2건을 기록합니다.
- 실패 시 잔액, 거래, journal, idempotency 기록이 함께 롤백되어야 합니다.
- reconciliation API는 저장된 계좌 잔액과 journal 기반 계산 잔액을 비교합니다.
- 계좌 journal 조회는 offset pagination 대신 keyset pagination으로 구현했습니다.
- demo profile은 반복 실행 시 Alice/Bob 계좌를 기준 잔액으로 journaled 방식 재정렬합니다.
- React Lab Console은 Vite proxy와 TanStack Query를 사용해 backend evidence flow를 시각적으로 실행합니다.

## 실험과 증거

### Rollback

서비스 중간에 의도적으로 `RuntimeException`을 발생시키는 failure point를 두었습니다. 출금 직후 실패하거나 transaction/journal row를 flush한 뒤 실패해도 최종 DB 상태에서 잔액, 거래 row, journal row가 남지 않는 것을 Testcontainers MySQL 통합 테스트로 검증했습니다.

### Idempotency

멱등성 기준은 `caller_scope + operation + idempotency_key`입니다. 같은 key와 같은 request fingerprint는 같은 결과를 재생하고, 같은 key지만 source/destination/amount가 다른 요청은 충돌로 거부합니다. 또한 같은 key의 동일 요청이 동시에 여러 번 들어와도 unique key 경쟁에서 이긴 하나의 이체 결과만 남고, 나머지는 완료된 결과를 replay하도록 검증했습니다. 같은 key에 서로 다른 body가 동시에 들어오는 경우도 하나의 성공과 하나의 conflict로 수렴하는지 검증했습니다.

### Concurrency

동시성은 세 가지 실험으로 비교했습니다.

- no-lock 실험: 두 요청이 같은 stale balance를 읽고 덮어써 reconciliation mismatch를 만들 수 있음을 증명했습니다.
- optimistic lock 실험: `@Version` 기반으로 두 stale writer 중 하나만 commit되고 하나는 rollback됨을 확인했고, API 경계에서는 해당 충돌을 `CONCURRENT_MODIFICATION` 409 응답으로 표현했습니다.
- pessimistic lock 실험: `PESSIMISTIC_WRITE`로 account row를 직렬화해 두 번째 요청이 최신 잔액을 보도록 만들었습니다. 실제 internal transfer는 두 계좌를 account id 순서로 잠가 반대 방향 동시 이체의 deadlock 위험을 줄였습니다.

### Reconciliation

정상적인 controlled seed funding과 internal transfer는 journal을 남기므로 reconciliation mismatch가 발생하지 않습니다. 반대로 테스트에서 계좌 balance만 직접 변경하면 reconciliation API가 mismatch를 탐지합니다.

### SQL

`account_journal_entry(account_id, id)` 인덱스를 추가하고, journal 조회를 `WHERE account_id = ? AND id < ? ORDER BY id DESC LIMIT ?` 형태의 keyset pagination으로 구현했습니다. 로컬 MySQL에서 인덱스 존재와 사용 가능한 `EXPLAIN` 결과를 evidence 문서에 기록했습니다.

### Lab Console

React/TypeScript/Vite 기반 Lab Console을 추가해 `GET /api/v1/demo/accounts`, `POST /api/v1/transfers/internal`, journal 조회, reconciliation 조회를 한 화면에서 실행하도록 만들었습니다. 같은 idempotency key로 동일 요청을 replay하면 첫 응답과 같은 transaction 결과를 보여주고, 같은 key로 amount만 바꾸면 의도된 409 conflict를 성공적인 검증 결과로 표시합니다.

`scripts/demo-frontend.sh`는 demo backend와 Vite dev server를 함께 띄운 뒤 frontend `/api` proxy를 통해 이체, replay, conflict, source/destination journal, reconciliation까지 자동 검증합니다. GitHub Actions CI도 백엔드 테스트, 프론트 lint/build, frontend proxy demo를 함께 실행합니다.

## 면접에서 강조할 포인트

- 기능 수를 늘리기보다 금융성 백엔드에서 중요한 실패 모드와 불변식을 먼저 정의했습니다.
- 테스트가 단순 happy path가 아니라 rollback, retry, duplicate request, stale write, lock strategy 차이를 검증합니다.
- JPA `ddl-auto`는 `validate`로 두고, schema source of truth는 Flyway로 관리했습니다.
- public API는 안전장치가 붙은 internal transfer만 열고, 비멱등 입금/출금 API 및 journal 없는 service-layer 입출금 경로는 의도적으로 제외했습니다.
- React Lab Console은 예쁜 껍데기보다 backend invariant를 보여주는 검증 도구로 설계했습니다.
- 자동 demo script와 CI가 실제 API 흐름을 반복 가능하게 검증합니다.
- 이 프로젝트는 “진짜 은행 시스템”이 아니라 “은행 IT에서 중요한 정확성 문제를 작게 재현한 실험형 백엔드”라고 설명하는 것이 가장 안전합니다.

## 이력서 문장 예시

Java/Spring Boot와 MySQL 기반 금융 이체 백엔드를 구현하며, 트랜잭션 롤백, 동시 멱등성 재시도, optimistic/pessimistic locking, reconciliation mismatch 탐지를 Testcontainers 통합 테스트, SQL evidence, React Lab Console demo로 검증했습니다.

## 30초 답변 예시

BankCore는 실제 은행 코어 전체가 아니라, 금융 이체 정확성에 집중한 실험형 프로젝트입니다. 이체 성공 시 transaction과 journal을 같이 남기고, 실패 시 잔액과 기록이 함께 롤백되는지 검증했습니다. 또한 idempotency key로 재시도와 동시 중복 이체를 막고, no-lock, optimistic lock, pessimistic lock을 비교했습니다. 마지막으로 React Lab Console과 자동 demo script로 이 증거 흐름을 한 화면과 CI에서 재현 가능하게 만들었습니다.
