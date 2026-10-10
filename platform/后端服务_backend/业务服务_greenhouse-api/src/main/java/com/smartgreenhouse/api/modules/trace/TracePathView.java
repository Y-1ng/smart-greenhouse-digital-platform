package com.smartgreenhouse.api.modules.trace;

import java.util.List;

public record TracePathView(
        String fieldNo,
        String productCode,
        boolean pointerChainValid,
        List<TraceView.TraceStageView> stages
) {
}
