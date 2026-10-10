package com.smartgreenhouse.api.modules.trace;

import com.smartgreenhouse.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class TraceQrCodeServiceTest {

    private static final String PRODUCT_CODE = "GH-TRACE-2026-0001";

    @Autowired
    private TraceQrCodeService service;

    @Test
    void payloadIsAPublicVerifyUrlInsteadOfFullTraceData() {
        TraceQrPayloadView view = service.describe(PRODUCT_CODE);

        assertThat(view.payload())
                // 当前默认配置：二维码指向本机公开溯源页 /trace，扫码后由该页查询明细
                .startsWith("http://localhost:5199/trace?code=")
                .contains(PRODUCT_CODE)
                .doesNotContain("signature-demo-value");
        assertThat(view.mode()).isEqualTo("query");
        assertThat(view.pngUrl()).endsWith("/qrcode?format=png");
        assertThat(view.svgUrl()).endsWith("/qrcode?format=svg");
    }

    @Test
    void rendersPngImage() {
        byte[] png = service.renderPng(PRODUCT_CODE, 240);

        assertThat(png.length).isGreaterThan(200);
        assertThat(png[0] & 0xFF).isEqualTo(0x89);
        assertThat(new String(png, 1, 3, StandardCharsets.US_ASCII)).isEqualTo("PNG");
    }

    @Test
    void rendersScalableSvg() {
        String svg = service.renderSvg(PRODUCT_CODE, 240);

        assertThat(svg)
                .contains("<svg")
                .contains("viewBox=\"0 0")
                .contains("<path d=\"M")
                .contains("aria-label=\"一物一码溯源码\"");
    }

    @Test
    void rejectsUnknownProductCode() {
        assertThatThrownBy(() -> service.describe("GH-TRACE-NOT-EXIST"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void fallsBackToQueryFormWhenGs1CompanyPrefixMissing() {
        TraceQrCodeService gs1WithoutPrefix = new TraceQrCodeService(
                null,
                "https://trace.example.com/",
                "gs1",
                "/traceability",
                "",
                320
        );

        assertThat(gs1WithoutPrefix.buildPayload("00001"))
                .isEqualTo("https://trace.example.com/traceability?code=00001");
    }

    @Test
    void buildsGs1DigitalLinkWithCheckDigit() {
        TraceQrCodeService gs1 = new TraceQrCodeService(
                null,
                "https://trace.example.com/",
                "gs1",
                "/traceability",
                "6901234",
                320
        );

        assertThat(gs1.buildPayload("00001"))
                .matches("https://trace\\.example\\.com/01/\\d{14}/21/00001");
    }

    @Test
    void computesGs1CheckDigitForKnownSample() {
        // GTIN-13 标准示例：400638133393 -> 校验位 1
        assertThat(TraceQrCodeService.gs1CheckDigit("400638133393"))
                .isEqualTo('1');
        assertThat(TraceQrCodeService.buildGtin14("6901234", "00001"))
                .hasSize(14)
                .startsWith("0690123400001");
    }
}
