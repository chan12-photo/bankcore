package com.bankcore.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "BankCore API",
                version = "0.0.1",
                description = "Synthetic banking transfer correctness backend with idempotency, journals, reconciliation, and pagination evidence."
        )
)
public class OpenApiConfig {
}
