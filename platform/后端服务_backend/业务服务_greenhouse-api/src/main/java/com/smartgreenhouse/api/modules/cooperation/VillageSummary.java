package com.smartgreenhouse.api.modules.cooperation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VillageSummary(
        Long villageId,
        String villageName,
        BigDecimal landArea,
        Long shareId,
        String projectName,
        BigDecimal shareRatio,
        LocalDate effectiveDate,
        BigDecimal dividendAmount,
        String status
) {
}
