package com.smartgreenhouse.api.integration.chain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedChainGatewayTest {

    @Test
    void returnsDeterministicConfirmedReceiptsAndIncrementsBlocks() {
        SimulatedChainGateway gateway = new SimulatedChainGateway(1284600L);

        ChainReceipt first =
                gateway.recordEnvironmentRoot("1001", "abc", "digest");
        ChainReceipt second =
                gateway.recordEnvironmentRoot("1001", "abc", "digest");

        assertThat(first.transactionHash()).matches("0x[0-9a-f]{64}");
        assertThat(first.transactionHash())
                .isEqualTo(second.transactionHash());
        assertThat(first.blockNumber()).isEqualTo(1284601L);
        assertThat(second.blockNumber()).isEqualTo(1284602L);
        assertThat(first.status()).isEqualTo("CONFIRMED");
        assertThat(first.confirmedAt()).isNotNull();
    }

    @Test
    void changesTransactionHashWhenPayloadChanges() {
        SimulatedChainGateway gateway = new SimulatedChainGateway(100L);

        ChainReceipt original =
                gateway.recordEnvironmentRoot("1001", "aaa", "digest");
        ChainReceipt changed =
                gateway.recordEnvironmentRoot("1001", "bbb", "digest");
        ChainReceipt otherFunction =
                gateway.recordCertificate("CERT-1", "aaa", "VALID");

        assertThat(original.transactionHash())
                .isNotEqualTo(changed.transactionHash());
        assertThat(original.transactionHash())
                .isNotEqualTo(otherFunction.transactionHash());
    }
}
