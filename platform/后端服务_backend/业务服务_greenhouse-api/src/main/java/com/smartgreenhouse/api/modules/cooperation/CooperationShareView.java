package com.smartgreenhouse.api.modules.cooperation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CooperationShareView(
        Long shareId,
        Long villageId,
        String projectName,
        BigDecimal shareRatio,
        LocalDate effectiveDate,
        String status
) {
}
