package com.bankcore;

import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class BankcoreApplicationTests extends MySqlContainerSupport {

    @Test
    void contextLoads() {
    }

}
