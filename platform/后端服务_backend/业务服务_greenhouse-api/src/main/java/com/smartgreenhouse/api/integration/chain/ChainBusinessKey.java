package com.smartgreenhouse.api.integration.chain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 链上业务标识：不能直接使用数据库自增 ID，否则演示库重置后会与链上历史记录冲突。
 */
public final class ChainBusinessKey {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private ChainBusinessKey() {
    }

    public static String environmentCycle(
            String greenhouseCode,
            LocalDateTime cycleStart,
            LocalDateTime cycleEnd
    ) {
        return "ENV-"
                + greenhouseCode
                + "-"
                + cycleStart.format(TIME_FORMAT)
                + "-"
                + cycleEnd.format(TIME_FORMAT);
    }
}
