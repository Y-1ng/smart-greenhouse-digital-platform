package com.smartgreenhouse.api.integration.chain;

import com.smartgreenhouse.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestChainGatewayTest {

    private static final String ADAPTER_URL = "http://localhost/chain/adapter";
    private static final String TRANSACTION_HASH = "0x" + "a".repeat(64);

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private RestChainGateway gateway;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://localhost");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new RestChainGateway(builder.build(), "/chain/adapter");
    }

    @Test
    void postsAdapterRequestAndMapsReceipt() {
        server.expect(requestTo(ADAPTER_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.method")
                        .value("recordEnvironmentRoot"))
                .andExpect(jsonPath("$.arguments.cycleId").value("1001"))
                .andRespond(withSuccess(
                        """
                        {"transactionHash":"%s","blockNumber":1284700,"status":"CONFIRMED"}
                        """.formatted(TRANSACTION_HASH),
                        MediaType.APPLICATION_JSON
                ));

        ChainReceipt receipt =
                gateway.recordEnvironmentRoot("1001", "root-hash", "digest");

        assertThat(receipt.transactionHash()).isEqualTo(TRANSACTION_HASH);
        assertThat(receipt.blockNumber()).isEqualTo(1284700L);
        assertThat(receipt.status()).isEqualTo("CONFIRMED");
        assertThat(receipt.confirmedAt()).isNotNull();
        server.verify();
    }

    @Test
    void sendsDifferentMethodForCertificate() {
        server.expect(requestTo(ADAPTER_URL))
                .andExpect(jsonPath("$.method").value("recordCertificate"))
                .andExpect(jsonPath("$.arguments.certificateId")
                        .value("CERT-1"))
                .andRespond(withSuccess(
                        """
                        {"transactionHash":"%s","blockNumber":1284800,"status":"CONFIRMED"}
                        """.formatted(TRANSACTION_HASH),
                        MediaType.APPLICATION_JSON
                ));

        ChainReceipt receipt =
                gateway.recordCertificate("CERT-1", "content-hash", "VALID");

        assertThat(receipt.blockNumber()).isEqualTo(1284800L);
        server.verify();
    }

    @Test
    void qualifiesBusinessKeysWithConfiguredNamespace() {
        RestClient.Builder namespacedBuilder =
                RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer namespacedServer =
                MockRestServiceServer.bindTo(namespacedBuilder).build();
        RestChainGateway namespacedGateway = new RestChainGateway(
                namespacedBuilder.build(),
                "/chain/adapter",
                new ChainBusinessNamespace("demo-run", "none")
        );
        namespacedServer.expect(requestTo(ADAPTER_URL))
                .andExpect(jsonPath("$.method").value("appendTraceNode"))
                .andExpect(jsonPath("$.arguments.fieldNo")
                        .value("demo-run-F-A-03"))
                .andRespond(withSuccess(
                        """
                        {"transactionHash":"%s","blockNumber":1284900,"status":"CONFIRMED"}
                        """.formatted(TRANSACTION_HASH),
                        MediaType.APPLICATION_JSON
                ));

        namespacedGateway.appendTraceNode(
                "F-A-03",
                0,
                "a".repeat(64),
                "0".repeat(64)
        );

        namespacedServer.verify();
    }

    @Test
    void throwsBusinessExceptionWhenAdapterFails() {
        server.expect(requestTo(ADAPTER_URL))
                .andRespond(withServerError());

        assertThatThrownBy(() ->
                gateway.appendScanRecord("GH-TRACE-1", "WECHAT_MINI", "FIRST_SCAN")
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("链上适配服务调用失败");
        server.verify();
    }
}
