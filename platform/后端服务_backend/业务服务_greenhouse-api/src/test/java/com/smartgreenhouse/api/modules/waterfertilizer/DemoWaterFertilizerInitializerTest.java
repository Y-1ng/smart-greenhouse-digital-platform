package com.smartgreenhouse.api.modules.waterfertilizer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoWaterFertilizerInitializerTest {

    @Test
    void createsThreePlansForEveryGreenhouse() {
        WaterFertilizerService service = mock(WaterFertilizerService.class);
        WaterFertilizerMapper mapper = mock(WaterFertilizerMapper.class);
        when(mapper.findAllGreenhouses()).thenReturn(List.of(
                new WaterFertilizerMapper.GreenhouseRow(1L, "GH-001"),
                new WaterFertilizerMapper.GreenhouseRow(2L, "GH-002")
        ));
        when(mapper.findFirstBatch(1L))
                .thenReturn(new WaterFertilizerMapper.BatchRow(2001L, "番茄"));
        when(mapper.findFirstBatch(2L))
                .thenReturn(new WaterFertilizerMapper.BatchRow(2002L, "草莓"));
        when(service.list(anyString(), any(LocalDate.class)))
                .thenReturn(List.of());
        when(service.create(any())).thenAnswer(invocation -> {
            WaterFertilizerController.CreatePlanRequest request =
                    invocation.getArgument(0);
            return plan(request.greenhouseCode(), request.zoneName(), request.plannedAt());
        });

        new DemoWaterFertilizerInitializer(service, mapper)
                .run(null);

        ArgumentCaptor<WaterFertilizerController.CreatePlanRequest> captor =
                ArgumentCaptor.forClass(
                        WaterFertilizerController.CreatePlanRequest.class
                );
        verify(service, times(6)).create(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(WaterFertilizerController.CreatePlanRequest::greenhouseCode)
                .containsExactly(
                        "GH-001", "GH-001", "GH-001",
                        "GH-002", "GH-002", "GH-002"
                );
        assertThat(captor.getAllValues())
                .extracting(request -> request.plannedAt().toLocalTime())
                .containsExactly(
                        LocalTime.of(11, 30),
                        LocalTime.of(15, 30),
                        LocalTime.of(19, 0),
                        LocalTime.of(11, 30),
                        LocalTime.of(15, 30),
                        LocalTime.of(19, 0)
                );
        assertThat(captor.getAllValues())
                .extracting(WaterFertilizerController.CreatePlanRequest::recipeCode)
                .containsExactly(
                        "TOMATO-FRUIT", "TOMATO-FRUIT", "TOMATO-FRUIT",
                        "STRAWBERRY-FRUIT", "STRAWBERRY-FRUIT", "STRAWBERRY-FRUIT"
                );
        verify(service, times(2)).execute(anyString(), any());
    }

    private WaterFertilizerPlanView plan(
            String greenhouseCode,
            String zoneName,
            LocalDateTime plannedAt
    ) {
        return new WaterFertilizerPlanView(
                1L,
                "WF-" + greenhouseCode + "-" + plannedAt.toLocalTime(),
                greenhouseCode,
                2001L,
                "番茄",
                "TOMATO-FRUIT",
                "V3",
                new BigDecimal("1.8"),
                new BigDecimal("6.2"),
                zoneName,
                plannedAt,
                new BigDecimal("2.1"),
                "PLANNED",
                "系统演示",
                null,
                null
        );
    }
}
