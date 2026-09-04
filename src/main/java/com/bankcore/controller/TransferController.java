package com.bankcore.controller;

import com.bankcore.controller.dto.InternalTransferRequest;
import com.bankcore.controller.dto.TransferResponse;
import com.bankcore.service.InternalTransferResult;
import com.bankcore.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/internal")
    public ResponseEntity<TransferResponse> transferInternal(
            @RequestHeader("X-Caller-Scope") String callerScope,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InternalTransferRequest request
    ) {
        InternalTransferResult result = transferService.transferInternalIdempotent(
                callerScope,
                idempotencyKey,
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount()
        );
        return ResponseEntity.ok(toResponse(result));
    }

    private static TransferResponse toResponse(InternalTransferResult result) {
        return new TransferResponse(
                result.transactionId(),
                result.transactionKey(),
                result.sourceAccountId(),
                result.destinationAccountId(),
                result.sourceBalanceAfter(),
                result.destinationBalanceAfter(),
                result.amount()
        );
    }
}
