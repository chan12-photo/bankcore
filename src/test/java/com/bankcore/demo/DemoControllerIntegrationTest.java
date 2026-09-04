package com.bankcore.demo;

import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@ImportTestcontainers(MySqlContainerSupport.class)
class DemoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void findDemoAccounts_shouldReturnSeededAliceAndBobAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/demo/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.customerName == 'Alice Demo' && @.balance == 100000)]").exists())
                .andExpect(jsonPath("$[?(@.customerName == 'Bob Demo' && @.balance == 30000)]").exists());
    }
}
