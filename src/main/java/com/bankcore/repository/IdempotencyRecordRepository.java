package com.bankcore.repository;

import com.bankcore.domain.IdempotencyOperation;
import com.bankcore.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByCallerScopeAndOperationAndIdempotencyKeyDigest(
            String callerScope,
            IdempotencyOperation operation,
            byte[] idempotencyKeyDigest
    );
}
