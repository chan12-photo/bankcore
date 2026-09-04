# BankCore 이력서/면접 노트

## 이력서 한 줄

Java/Spring Boot와 MySQL 기반 금융 이체 백엔드를 구현하며, 트랜잭션 롤백, 동시 멱등성 재시도, 동시성 제어, 정산 검증, keyset pagination 성능 근거를 통합 테스트와 SQL evidence로 검증했습니다.

## 이력서 Bullet 후보

- Java 25, Spring Boot 4.1, MySQL 8.4, Flyway, JPA, Testcontainers 기반 금융 이체 정확성 검증 백엔드 구현
- 내부이체 성공 시 transaction 1건과 account journal 2건을 기록하고, 실패 주입 테스트로 flush 이후 예외에서도 잔액/거래/journal/idempotency row가 함께 롤백됨을 검증
- `caller_scope + operation + idempotency_key` 기반 멱등성 모델을 구현해 동일 요청 재시도와 동시 같은-key 요청은 하나의 결과로 replay하고, 다른 fingerprint 재사용은 conflict로 거부
- no-lock, optimistic lock, pessimistic lock 동시성 실험을 구성해 stale write로 인한 불일치와 locking 전략별 정합성 보존을 Testcontainers MySQL에서 검증
- stored balance와 journal-derived balance를 비교하는 reconciliation API를 구현하고, 직접 잔액 변조 및 정상 journaled flow를 통해 mismatch 탐지 여부 검증
- account journal 조회에 `(account_id, id)` 인덱스와 keyset pagination을 적용하고, 50,000건 synthetic journal 기준 offset pagination 대비 접근 패턴과 `EXPLAIN ANALYZE` 결과 문서화

## 가장 추천하는 이력서 Bullet 3개

- Java/Spring Boot와 MySQL 기반 금융 이체 정확성 검증 백엔드를 구현하고, rollback, idempotency, reconciliation, concurrency failure를 Testcontainers 통합 테스트로 증명
- `Idempotency-Key` 기반 내부이체 API를 설계해 동일 요청 재시도와 동시 중복 요청은 같은 결과를 replay하고, 다른 request fingerprint 재사용은 conflict로 거부하도록 구현
- no-lock/optimistic lock/pessimistic lock 실험을 통해 stale balance overwrite와 row-level serialization 차이를 재현하고 SQL evidence로 정리

## 면접 30초 답변

BankCore는 실제 은행 코어 전체가 아니라 금융 이체 정확성에 집중한 실험형 백엔드입니다. Spring Boot와 MySQL로 내부이체 API를 만들고, 이체 성공 시 transaction과 journal을 함께 기록하도록 했습니다. 중간 예외가 발생해도 잔액과 기록이 함께 롤백되는지 검증했고, `Idempotency-Key`로 재시도 및 동시 중복 이체를 막았습니다. 또 no-lock, optimistic lock, pessimistic lock 실험을 만들어 동시성 문제가 어떻게 생기고 어떤 전략으로 막히는지 Testcontainers MySQL에서 증명했습니다.

## 면접 1분 답변

BankCore는 Java/Spring Boot와 MySQL로 만든 금융 이체 정확성 검증 프로젝트입니다. 처음에는 은행 코어뱅킹처럼 넓게 잡을 수 있었지만, 포트폴리오에서 방어 가능한 깊이를 만들기 위해 범위를 이체 정확성으로 줄였습니다. 공개 입금/출금 API는 중복 요청 위험이 커서 제외했고, public money movement는 멱등성 헤더가 필수인 internal transfer로 제한했습니다.

핵심 검증은 네 가지입니다. 첫째, 이체 성공 시 transaction 1건과 journal 2건이 남습니다. 둘째, 출금 직후 또는 journal flush 이후 예외가 발생해도 모든 변경이 롤백됩니다. 셋째, 같은 idempotency key와 같은 요청은 재시도와 동시 호출 모두에서 같은 결과를 replay하고, 같은 key로 다른 요청을 보내면 conflict를 반환합니다. 넷째, no-lock, optimistic lock, pessimistic lock 실험으로 동시성 실패와 방어 전략을 비교했습니다. 추가로 stored balance와 journal-derived balance를 비교하는 reconciliation API와 journal keyset pagination 성능 evidence도 남겼습니다.

## 예상 질문과 답변

### 왜 입금/출금 API를 뺐나요?

공개 입금/출금은 외부에서 돈을 만들거나 없애는 명령처럼 보이고, 멱등성 없이 열면 재시도만으로 중복 금액 반영이 발생할 수 있습니다. 그래서 MVP에서는 public money movement를 internal transfer로 제한했고, 입금은 테스트 데이터 준비를 위한 controlled seed funding으로만 남겼습니다.

### 멱등성은 어떻게 보장했나요?

`caller_scope + operation + idempotency_key`에 unique constraint를 걸었습니다. 요청 fingerprint에는 version, operation, currency, source account, destination account, amount를 포함했습니다. 같은 key와 같은 fingerprint는 기존 transaction/journal 결과를 replay하고, 같은 key지만 fingerprint가 다르면 conflict로 거부합니다. 동시 같은-key 요청에서는 하나만 insert와 이체에 성공하고, unique key 경쟁에서 진 요청은 새 transaction에서 완료된 idempotency record를 다시 읽어 replay합니다.

### rollback은 어떻게 검증했나요?

service 내부에 테스트용 failure point를 두고 출금 직후, 그리고 transaction/journal flush 이후에 `RuntimeException`을 발생시켰습니다. 테스트 자체는 test-managed transaction으로 감싸지 않고, 예외 이후 repository로 최종 DB 상태를 다시 조회해 잔액과 row count가 원래대로인지 검증했습니다.

### optimistic lock과 pessimistic lock 차이를 어떻게 보여줬나요?

optimistic lock 실험은 두 트랜잭션이 같은 version을 읽은 뒤 동시에 갱신하게 만들어 하나만 commit되고 하나는 rollback되는 결과를 확인했습니다. pessimistic lock 실험은 source account row를 `PESSIMISTIC_WRITE`로 잠가 두 번째 요청이 첫 번째 commit 이후 최신 잔액을 보게 만들었습니다.

### reconciliation은 왜 넣었나요?

잔액 row만 보면 문제가 숨어 있을 수 있습니다. 그래서 journal entry를 기준으로 계산한 잔액과 account table의 stored balance를 비교하는 reconciliation API를 만들었습니다. 정상 journaled flow는 mismatch가 없고, 직접 balance만 바꾸는 테스트는 mismatch로 탐지됩니다.

### 이 프로젝트가 실제 은행 시스템인가요?

아닙니다. 이 프로젝트는 실제 계정계, 원장, 인증, 권한, 규제, FDS, 결제망 연동을 구현하지 않습니다. 대신 은행 IT 백엔드에서 중요한 트랜잭션 정확성 문제를 작게 재현하고 테스트와 SQL evidence로 증명하는 프로젝트입니다.
