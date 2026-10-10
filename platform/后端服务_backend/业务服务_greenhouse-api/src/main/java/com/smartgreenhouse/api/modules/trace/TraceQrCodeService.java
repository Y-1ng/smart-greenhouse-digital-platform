package com.smartgreenhouse.api.modules.trace;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * 一物一码二维码生成（ZXing）。
 *
 * 二维码内容遵循一条原则：码里只放"去哪儿校验"，不放追溯明细。
 * 默认写成 {@code https://域名/traceability?code=产品编码}，
 * 配置了真实 GS1 厂商识别码时也可以写成 Digital Link：
 * {@code https://域名/01/{GTIN}/21/{序列号}}。
 */
@Service
public class TraceQrCodeService {

    private static final Logger log = LoggerFactory.getLogger(TraceQrCodeService.class);

    private static final String MODE_GS1 = "gs1";
    private static final String DEFAULT_MODE = "query";
    private static final int MIN_SIZE = 96;
    private static final int MAX_SIZE = 1024;
    /** 二维码静区，按模块计；印刷时需要留白，太小会扫不出来 */
    private static final int QUIET_ZONE = 2;
    private static final String FOREGROUND = "#12241e";
    private static final String BACKGROUND = "#ffffff";
    private static final String QR_PATH = "/api/trace-codes";

    private final TraceMapper mapper;
    private final String publicBaseUrl;
    private final String configuredMode;
    private final String queryPath;
    private final String gs1CompanyPrefix;
    private final int defaultSize;

    public TraceQrCodeService(
            TraceMapper mapper,
            @Value("${app.trace.qr.public-base-url:https://greenhouse.example.com}")
            String publicBaseUrl,
            @Value("${app.trace.qr.mode:query}") String configuredMode,
            @Value("${app.trace.qr.query-path:/traceability}") String queryPath,
            @Value("${app.trace.qr.gs1-company-prefix:}") String gs1CompanyPrefix,
            @Value("${app.trace.qr.size:320}") int defaultSize
    ) {
        this.mapper = mapper;
        this.publicBaseUrl = publicBaseUrl;
        this.configuredMode = configuredMode;
        this.queryPath = queryPath;
        this.gs1CompanyPrefix = gs1CompanyPrefix;
        this.defaultSize = defaultSize;
    }

    @Transactional(readOnly = true)
    public TraceQrPayloadView describe(String productCode) {
        String code = requireTraceCode(productCode);
        return new TraceQrPayloadView(
                code,
                buildPayload(code),
                effectiveMode(),
                normalizeSize(null),
                QR_PATH + "/" + encodePath(code) + "/qrcode?format=png",
                QR_PATH + "/" + encodePath(code) + "/qrcode?format=svg"
        );
    }

    @Transactional(readOnly = true)
    public byte[] renderPng(String productCode, Integer size) {
        String code = requireTraceCode(productCode);
        int target = normalizeSize(size);
        BitMatrix matrix = encode(buildPayload(code), target, target);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(
                    matrix,
                    "PNG",
                    output,
                    new MatrixToImageConfig(0xFF12241E, 0xFFFFFFFF)
            );
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "二维码生成失败：" + exception.getMessage()
            );
        }
    }

    @Transactional(readOnly = true)
    public String renderSvg(String productCode, Integer size) {
        String code = requireTraceCode(productCode);
        int target = normalizeSize(size);
        // 传 1×1 让 ZXing 返回"一个模块一个像素"的原始矩阵，SVG 才能真正无损缩放
        BitMatrix matrix = encode(buildPayload(code), 1, 1);
        return toSvg(matrix, target);
    }

    /**
     * 二维码内容：默认查询串形式；配置了厂商识别码时可用 GS1 Digital Link。
     */
    public String buildPayload(String productCode) {
        String base = trimTrailingSlash(publicBaseUrl);
        if (MODE_GS1.equals(effectiveMode())) {
            String gtin = buildGtin14(gs1CompanyPrefix, productCode);
            if (gtin != null) {
                return base + "/01/" + gtin + "/21" + "/" + gs1Serial(productCode);
            }
            log.warn(
                    "二维码模式为 gs1 但未配置 app.trace.qr.gs1-company-prefix，已回退为查询串形式"
            );
        }
        return base + normalizeQueryPath() + "?code="
                + URLEncoder.encode(productCode, StandardCharsets.UTF_8);
    }

    /**
     * GTIN-14：厂商识别码 + 商品项目代码补齐 13 位，最后一位是 GS1 校验位。
     * 未配置厂商识别码时返回 null，由调用方回退到查询串形式。
     */
    static String buildGtin14(String companyPrefix, String productCode) {
        String prefix = digitsOnly(companyPrefix);
        if (prefix.isEmpty()) {
            return null;
        }
        String itemReference = digitsOnly(productCode);
        String body = prefix + itemReference;
        if (body.length() > 13) {
            body = body.substring(0, 13);
        }
        body = "0".repeat(13 - body.length()) + body;
        return body + gs1CheckDigit(body);
    }

    /** GS1 标准校验位：从右往左 3、1 交替加权 */
    static char gs1CheckDigit(String body) {
        int sum = 0;
        for (int index = 0; index < body.length(); index++) {
            int digit = body.charAt(body.length() - 1 - index) - '0';
            sum += (index % 2 == 0) ? digit * 3 : digit;
        }
        int remainder = sum % 10;
        return (char) ('0' + (remainder == 0 ? 0 : 10 - remainder));
    }

    private String requireTraceCode(String productCode) {
        String code = productCode == null ? "" : productCode.trim();
        if (code.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "产品编码不能为空");
        }
        if (mapper.findByProductCode(code) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "产品编码不存在：" + code);
        }
        return code;
    }

    private BitMatrix encode(String payload, int width, int height) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, QUIET_ZONE);
        try {
            return new QRCodeWriter().encode(
                    payload,
                    BarcodeFormat.QR_CODE,
                    width,
                    height,
                    hints
            );
        } catch (WriterException exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "二维码编码失败：" + exception.getMessage()
            );
        }
    }

    private String toSvg(BitMatrix matrix, int size) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < height; y++) {
            int x = 0;
            while (x < width) {
                if (!matrix.get(x, y)) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < width && matrix.get(x, y)) {
                    x++;
                }
                int run = x - start;
                path.append('M').append(start).append(' ').append(y)
                        .append('h').append(run)
                        .append("v1h-").append(run).append('z');
            }
        }
        return new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\"")
                .append(" width=\"").append(size).append('"')
                .append(" height=\"").append(size).append('"')
                .append(" viewBox=\"0 0 ").append(width).append(' ').append(height).append('"')
                .append(" shape-rendering=\"crispEdges\" role=\"img\"")
                .append(" aria-label=\"一物一码溯源码\">\n")
                .append("  <rect width=\"").append(width).append('"')
                .append(" height=\"").append(height).append('"')
                .append(" fill=\"").append(BACKGROUND).append("\"/>\n")
                .append("  <path d=\"").append(path).append('"')
                .append(" fill=\"").append(FOREGROUND).append("\"/>\n")
                .append("</svg>\n")
                .toString();
    }

    private int normalizeSize(Integer size) {
        int value = size == null ? defaultSize : size;
        if (value < MIN_SIZE) {
            value = MIN_SIZE;
        }
        if (value > MAX_SIZE) {
            value = MAX_SIZE;
        }
        return value;
    }

    private String effectiveMode() {
        return MODE_GS1.equalsIgnoreCase(configuredMode == null ? "" : configuredMode.trim())
                ? MODE_GS1
                : DEFAULT_MODE;
    }

    private String normalizeQueryPath() {
        String path = queryPath == null || queryPath.isBlank()
                ? "/traceability"
                : queryPath.trim();
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String trimTrailingSlash(String value) {
        String base = value == null || value.isBlank()
                ? "https://greenhouse.example.com"
                : value.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String digitsOnly(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder digits = new StringBuilder();
        for (char character : value.toCharArray()) {
            if (Character.isDigit(character)) {
                digits.append(character);
            }
        }
        return digits.toString();
    }

    /** GS1 AI(21) 序列号：最长 20 位，去掉 URL 里需要转义的字符 */
    private static String gs1Serial(String productCode) {
        String serial = productCode.replaceAll("[^0-9A-Za-z]", "");
        if (serial.length() > 20) {
            serial = serial.substring(0, 20);
        }
        return serial.isEmpty()
                ? URLEncoder.encode(productCode, StandardCharsets.UTF_8)
                : serial;
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
