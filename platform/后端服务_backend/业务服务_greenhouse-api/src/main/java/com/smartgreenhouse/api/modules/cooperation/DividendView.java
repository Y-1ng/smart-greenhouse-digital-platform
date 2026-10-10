package com.smartgreenhouse.api.modules.cooperation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DividendView(
        Long dividendId,
        Long shareId,
        String periodName,
        BigDecimal amount,
        String status,
        LocalDateTime confirmedAt
) {
}
