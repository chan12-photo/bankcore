package com.bankcore;

import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

@SpringBootTest
@ImportTestcontainers(MySqlContainerSupport.class)
class BankcoreApplicationTests {

    @Test
    void contextLoads() {
    }

}
