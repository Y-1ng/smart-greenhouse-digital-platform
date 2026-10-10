package com.smartgreenhouse.iot.business;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 与业务服务的接口契约：/api/control/commands/pending 返回的是完整指令视图
 * （含 operator、status、retryCount 等本服务不用的字段），解析必须容忍未知字段。
 */
class BusinessApiClientContractTest {

    @Test
    void parsesPendingCommandResponseWithExtraFields() {
        String body = """
                {
                  "code": "0",
                  "message": "OK",
                  "requestId": "req-1",
                  "data": [
                    {
                      "commandNo": "CMD-8f1c",
                      "greenhouseCode": "GH-001",
                      "deviceCode": "FAN-A-02",
                      "commandType": "FAN_ON",
                      "parameters": {"speed": 68, "enabled": true},
                      "operator": "基地管理员",
                      "timeoutSeconds": 30,
                      "status": "CREATED",
                      "failureReason": null,
                      "createdAt": "2026-09-19T18:00:00",
                      "dispatchedAt": null,
                      "acknowledgedAt": null,
                      "completedAt": null,
                      "retryCount": 0
                    }
                  ]
                }
                """;

        List<BusinessApiClient.DeviceCommand> commands = client()
                .parsePendingCommands(body);

        assertThat(commands).hasSize(1);
        BusinessApiClient.DeviceCommand command = commands.get(0);
        assertThat(command.commandNo()).isEqualTo("CMD-8f1c");
        assertThat(command.greenhouseCode()).isEqualTo("GH-001");
        assertThat(command.deviceCode()).isEqualTo("FAN-A-02");
        assertThat(command.commandType()).isEqualTo("FAN_ON");
        assertThat(command.timeoutSeconds()).isEqualTo(30);
        assertThat(command.parameters())
                .containsEntry("speed", 68)
                .containsEntry("enabled", true);
    }

    @Test
    void returnsEmptyListWhenNoPendingCommand() {
        assertThat(client().parsePendingCommands("""
                {"code": "0", "message": "OK", "data": []}
                """)).isEmpty();
        assertThat(client().parsePendingCommands(null)).isEmpty();
    }

    private BusinessApiClient client() {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        false
                )
                .findAndRegisterModules();
        return new BusinessApiClient(
                "http://localhost:8090",
                "iot-service",
                "123",
                objectMapper
        );
    }
}
