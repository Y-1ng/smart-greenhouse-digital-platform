package com.smartgreenhouse.api.integration.chain;

import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChainInvokerTest {

    @Test
    void returnsPendingReceiptWhenGatewayThrows() {
        ChainGateway gateway = mock(ChainGateway.class);
        when(gateway.recordEnvironmentRoot(
                anyString(),
                anyString(),
                anyString()
        )).thenThrow(new BusinessException(
                ErrorCode.INTERNAL_ERROR,
                "链上服务不可用"
        ));

        ChainReceipt receipt = new ChainInvoker(gateway)
                .recordEnvironmentRoot("1001", "root", "digest");

        assertThat(receipt.status()).isEqualTo("PENDING");
        assertThat(receipt.transactionHash()).isNull();
        assertThat(receipt.blockNumber()).isZero();
    }

    @Test
    void passesThroughSuccessfulReceipt() {
        ChainGateway gateway = mock(ChainGateway.class);
        ChainReceipt expected = new ChainReceipt(
                "0xabc",
                1284701L,
                "CONFIRMED",
                Instant.now()
        );
        when(gateway.appendScanRecord(
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(expected);

        ChainReceipt receipt = new ChainInvoker(gateway)
                .appendScanRecord("GH-TRACE-1", "WECHAT_MINI", "FIRST_SCAN");

        assertThat(receipt).isSameAs(expected);
    }
}
