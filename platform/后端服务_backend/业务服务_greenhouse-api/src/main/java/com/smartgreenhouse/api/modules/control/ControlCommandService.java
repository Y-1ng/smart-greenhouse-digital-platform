package com.smartgreenhouse.api.modules.control;

import com.smartgreenhouse.api.integration.cache.JsonCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ControlCommandService {

    private static final Logger log =
            LoggerFactory.getLogger(ControlCommandService.class);

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final ControlCommandMapper mapper;
    private final ObjectMapper objectMapper;
    private final JsonCache cache;
    private final int maxRetries;
    private final int timeoutScanBatchSize;

    public ControlCommandService(
            ControlCommandMapper mapper,
            ObjectMapper objectMapper,
            JsonCache cache,
            @Value("${app.control.timeout-scan.max-retries:1}") int maxRetries,
            @Value("${app.control.timeout-scan.batch-size:50}")
            int timeoutScanBatchSize
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.cache = cache;
        this.maxRetries = maxRetries;
        this.timeoutScanBatchSize = timeoutScanBatchSize;
    }

    @Transactional
    public ControlCommandView create(ControlController.ControlCommandRequest request) {
        Long greenhouseId = mapper.findGreenhouseId(request.greenhouseCode());
        if (greenhouseId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "温室不存在");
        }

        Long deviceId = mapper.findDeviceId(greenhouseId, request.deviceCode());
        if (deviceId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在或不属于该温室");
        }

        String commandNo = "CMD-" + UUID.randomUUID();
        mapper.insert(new ControlCommandMapper.ControlCommandInsert(
                commandNo,
                greenhouseId,
                deviceId,
                request.commandType(),
                serializeParameters(request.parameters()),
                request.operator(),
                request.timeoutSeconds() == null
                        ? DEFAULT_TIMEOUT_SECONDS
                        : request.timeoutSeconds()
        ));
        return get(commandNo);
    }

    @Transactional(readOnly = true)
    public ControlCommandView get(String commandNo) {
        ControlCommandMapper.CommandRow command =
                mapper.findByCommandNo(commandNo);
        if (command == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "控制指令不存在");
        }
        return toView(command);
    }

    /**
     * 待下发指令：供物联网接入服务轮询后发布到 MQTT。
     */
    @Transactional(readOnly = true)
    public List<ControlCommandView> pending(int limit) {
        return mapper.findPendingDispatch(limit).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * 标记指令已下发到设备，重复调用保持幂等。
     */
    @Transactional
    public ControlCommandView markDispatched(String commandNo) {
        ControlCommandMapper.CommandRow existing =
                mapper.findByCommandNo(commandNo);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "控制指令不存在");
        }
        if ("CREATED".equals(existing.status())) {
            mapper.markDispatched(commandNo, LocalDateTime.now());
        }
        return get(commandNo);
    }

    /**
     * 已下发但未收到回执的指令，供设备模拟器或运维排查使用。
     */
    @Transactional(readOnly = true)
    public List<ControlCommandView> dispatched(int limit) {
        return mapper.findDispatched(limit).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * 超时扫描：超过 timeoutSeconds 仍未收到回执的指令先重试，超过重试上限则置为失败。
     */
    @Transactional
    public int applyTimeouts() {
        LocalDateTime now = LocalDateTime.now();
        List<ControlCommandMapper.CommandRow> openCommands =
                mapper.findOpenCommands(timeoutScanBatchSize);
        int handled = 0;
        for (ControlCommandMapper.CommandRow command : openCommands) {
            LocalDateTime startedAt = command.attemptStartedAt() == null
                    ? command.createdAt()
                    : command.attemptStartedAt();
            if (startedAt == null
                    || startedAt.plusSeconds(command.timeoutSeconds())
                    .isAfter(now)) {
                continue;
            }
            int retries = command.retryCount() == null
                    ? 0
                    : command.retryCount();
            if (retries < maxRetries) {
                mapper.retry(
                        command.commandNo(),
                        now,
                        "指令超时未收到回执，第 " + (retries + 1) + " 次重试"
                );
            } else {
                mapper.fail(
                        command.commandNo(),
                        "超过 " + (maxRetries + 1) + " 次尝试仍未收到设备回执",
                        now
                );
            }
            handled++;
        }
        if (handled > 0) {
            log.info("控制指令超时处理完成：本轮处理 {} 条", handled);
        }
        return handled;
    }

    private ControlCommandView toView(ControlCommandMapper.CommandRow command) {
        return new ControlCommandView(
                command.commandNo(),
                command.greenhouseCode(),
                command.deviceCode(),
                command.commandType(),
                deserializeParameters(command.commandPayload()),
                command.operatorName(),
                command.timeoutSeconds(),
                command.status(),
                command.failureReason(),
                command.createdAt(),
                command.dispatchedAt(),
                command.acknowledgedAt(),
                command.completedAt(),
                command.retryCount() == null ? 0 : command.retryCount()
        );
    }

    @Transactional
    public ControlCommandView acknowledge(
            String commandNo,
            ControlController.AckRequest request
    ) {
        ControlCommandMapper.CommandRow existing =
                mapper.findByCommandNo(commandNo);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "控制指令不存在");
        }
        if ("SUCCEEDED".equals(existing.status())
                || "FAILED".equals(existing.status())) {
            return get(commandNo);
        }

        LocalDateTime executedAt = request.executedAt() == null
                ? LocalDateTime.now()
                : LocalDateTime.parse(request.executedAt());
        String status = Boolean.TRUE.equals(request.success())
                ? "SUCCEEDED"
                : "FAILED";
        mapper.acknowledge(new ControlCommandMapper.AcknowledgementUpdate(
                commandNo,
                status,
                status.equals("FAILED") ? request.message() : null,
                executedAt,
                executedAt
        ));
        if ("SUCCEEDED".equals(status)) {
            mapper.updateDeviceOperatingState(
                    existing.deviceId(),
                    operatingStateFor(existing.commandType())
            );
            // 设备运行状态变了，温室总览缓存立即失效，避免界面看到旧状态
            cache.evict("overview:" + existing.greenhouseCode());
        }
        return get(commandNo);
    }

    private String operatingStateFor(String commandType) {
        if (commandType.endsWith("_OFF")
                || commandType.endsWith("_CLOSE")) {
            return "OFF";
        }
        return "ON";
    }

    private String serializeParameters(Map<String, Object> parameters) {
        try {
            return objectMapper.writeValueAsString(
                    parameters == null ? Map.of() : parameters
            );
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "控制参数无法序列化");
        }
    }

    private Map<String, Object> deserializeParameters(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            try {
                String embeddedJson = objectMapper.readValue(
                        payload,
                        String.class
                );
                return objectMapper.readValue(
                        embeddedJson,
                        new TypeReference<>() {
                        }
                );
            } catch (JsonProcessingException nestedException) {
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "控制参数无法解析"
                );
            }
        }
    }
}
