package com.smartgreenhouse.api.modules.trace;

/**
 * 二维码载荷：二维码里只放一个可公开的校验地址，不放追溯明细。
 * 前端用 payload 做本地预览，保证预览内容与后端生成的图片完全一致。
 */
public record TraceQrPayloadView(
        String productCode,
        String payload,
        String mode,
        int size,
        String pngUrl,
        String svgUrl
) {
}
