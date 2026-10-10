package com.smartgreenhouse.api.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanAddressResolverTest {

    @Test
    void resolvesAnIpv4Literal() {
        assertThat(LanAddressResolver.resolve())
                .matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }
}
