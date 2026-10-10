package com.smartgreenhouse.api.modules.environment;

import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class EnvironmentThresholdService {

    private final EnvironmentThresholdMapper mapper;

    public EnvironmentThresholdService(EnvironmentThresholdMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<EnvironmentThresholdView> list(String greenhouseCode) {
        Long greenhouseId = requireGreenhouse(greenhouseCode);
        return mapper.findByGreenhouseId(greenhouseId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public EnvironmentThresholdView update(
            String metricCode,
            EnvironmentThresholdController.UpdateRequest request
    ) {
        Long greenhouseId = requireGreenhouse(request.greenhouseCode());
        validate(request);
        EnvironmentThresholdMapper.ThresholdUpdate update =
                new EnvironmentThresholdMapper.ThresholdUpdate(
                        greenhouseId,
                        metricCode,
                        request.targetMin(),
                        request.targetMax(),
                        request.alertMin(),
                        request.alertMax(),
                        request.controlAction(),
                        request.enabled()
                );
        if (mapper.update(update) == 0) {
            mapper.insert(update);
        }
        return toView(mapper.findOne(greenhouseId, metricCode));
    }

    private Long requireGreenhouse(String greenhouseCode) {
        Long greenhouseId = mapper.findGreenhouseId(greenhouseCode);
        if (greenhouseId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "温室不存在");
        }
        return greenhouseId;
    }

    private void validate(
            EnvironmentThresholdController.UpdateRequest request
    ) {
        if (request.targetMin().compareTo(request.targetMax()) > 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "目标下限不能大于上限"
            );
        }
        if (request.alertMin().compareTo(request.targetMin()) > 0
                || request.alertMax().compareTo(request.targetMax()) < 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "告警范围必须覆盖目标范围"
            );
        }
        if (request.alertMin().compareTo(request.alertMax()) > 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "告警下限不能大于上限"
            );
        }
        if (request.controlAction() == null
                || request.controlAction().isBlank()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "控制动作不能为空"
            );
        }
    }

    private EnvironmentThresholdView toView(
            EnvironmentThresholdMapper.ThresholdRow threshold
    ) {
        return new EnvironmentThresholdView(
                threshold.thresholdId(),
                threshold.metricCode(),
                threshold.targetMin(),
                threshold.targetMax(),
                threshold.alertMin(),
                threshold.alertMax(),
                threshold.controlAction(),
                threshold.enabled(),
                threshold.updatedAt()
        );
    }
}
