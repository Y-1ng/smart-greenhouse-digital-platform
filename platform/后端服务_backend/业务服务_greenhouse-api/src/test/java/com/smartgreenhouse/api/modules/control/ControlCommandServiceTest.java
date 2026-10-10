package com.smartgreenhouse.api.modules.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgreenhouse.api.integration.cache.JsonCache;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControlCommandServiceTest {

    @Test
    void readsDoubleEncodedJsonParameters() {
        ControlCommandMapper mapper = mock(ControlCommandMapper.class);
        when(mapper.findByCommandNo("CMD-TEST")).thenReturn(
                new ControlCommandMapper.CommandRow(
                        "CMD-TEST",
                        1L,
                        "GH-001",
                        "FAN-A-02",
                        "FAN_ON",
                        "\"{\\\"speed\\\":68,\\\"enabled\\\":true}\"",
                        "基地管理员",
                        30,
                        "CREATED",
                        null,
                        LocalDateTime.now(),
                        null,
                        null,
                        null,
                        LocalDateTime.now(),
                        0
                )
        );

        ControlCommandService service = new ControlCommandService(
                mapper,
                new ObjectMapper(),
                mock(JsonCache.class),
                1,
                50
        );

        assertThat(service.get("CMD-TEST").parameters())
                .containsEntry("speed", 68)
                .containsEntry("enabled", true);
    }
}
