package com.smartgreenhouse.api.integration.chain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 app.chain.mode=rest 时真实链网关能否被 Spring 正常装配。
 * 其余用例使用 simulated 模式，不会覆盖这条装配路径。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.chain.mode=rest",
        "app.chain.endpoint=http://localhost:19999"
})
class RestChainGatewayContextTest {

    @Autowired
    private ChainGateway gateway;

    @Test
    void wiresRestGatewayWhenModeIsRest() {
        assertThat(gateway).isInstanceOf(RestChainGateway.class);
    }
}
