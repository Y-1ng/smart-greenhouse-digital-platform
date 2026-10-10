package com.smartgreenhouse.api.integration.chain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChainBusinessNamespaceTest {

    @Test
    void leavesBusinessKeyUnchangedByDefault() {
        ChainBusinessNamespace namespace =
                new ChainBusinessNamespace("", "none");

        assertThat(namespace.qualify("F-A-03")).isEqualTo("F-A-03");
    }

    @Test
    void generatesIsolatedPrefixInAutoMode() {
        ChainBusinessNamespace first =
                new ChainBusinessNamespace("", "auto");
        ChainBusinessNamespace second =
                new ChainBusinessNamespace("", "auto");

        assertThat(first.qualify("F-A-03"))
                .startsWith("demo-")
                .endsWith("-F-A-03");
        assertThat(second.qualify("F-A-03"))
                .startsWith("demo-")
                .isNotEqualTo(first.qualify("F-A-03"));
    }
}
