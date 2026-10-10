package com.smartgreenhouse.api.modules.waterfertilizer;

import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class WaterFertilizerService {

    private static final DateTimeFormatter PLAN_DATE_FORMAT =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final WaterFertilizerMapper mapper;

    public WaterFertilizerService(WaterFertilizerMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<WaterFertilizerPlanView> list(
            String greenhouseCode,
            LocalDate planDate
    ) {
        requireGreenhouse(greenhouseCode);
        LocalDate effectiveDate = planDate == null
                ? LocalDate.now()
                : planDate;
        return mapper.findPlans(greenhouseCode, effectiveDate).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public WaterFertilizerPlanView create(
            WaterFertilizerController.CreatePlanRequest request
    ) {
        validate(request);
        WaterFertilizerMapper.GreenhouseRow greenhouse =
                requireGreenhouse(request.greenhouseCode());
        WaterFertilizerMapper.BatchRow batch = mapper.findBatch(
                request.batchId(),
                greenhouse.greenhouseId()
        );
        if (batch == null) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "作物批次不属于目标温室"
            );
        }

        String planNo = nextPlanNo(request.plannedAt().toLocalDate());
        mapper.insert(new WaterFertilizerMapper.PlanInsert(
                planNo,
                greenhouse.greenhouseId(),
                request.batchId(),
                request.recipeCode(),
                request.recipeVersion(),
                request.ecTarget(),
                request.phTarget(),
                request.zoneName(),
                request.plannedAt(),
                request.waterVolume(),
                request.operatorName()
        ));
        return toView(mapper.findByPlanNo(planNo));
    }

    @Transactional
    public WaterFertilizerPlanView execute(
            String planNo,
            WaterFertilizerController.ExecutePlanRequest request
    ) {
        if (request.operatorName() == null
                || request.operatorName().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "执行人不能为空");
        }

        WaterFertilizerMapper.PlanRow existing =
                mapper.findByPlanNo(planNo);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "水肥计划不存在");
        }
        if (!"PLANNED".equals(existing.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "水肥计划已执行");
        }

        LocalDateTime executedAt = request.executedAt() == null
                ? LocalDateTime.now()
                : request.executedAt();
        if (mapper.markSucceeded(
                planNo,
                request.operatorName(),
                executedAt
        ) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "水肥计划状态已变化");
        }
        return toView(mapper.findByPlanNo(planNo));
    }

    private void validate(WaterFertilizerController.CreatePlanRequest request) {
        if (request.greenhouseCode() == null
                || request.greenhouseCode().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "温室编码不能为空");
        }
        if (request.batchId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "作物批次不能为空");
        }
        if (request.recipeCode() == null || request.recipeCode().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "配方编码不能为空");
        }
        if (request.recipeVersion() == null
                || request.recipeVersion().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "配方版本不能为空");
        }
        if (request.ecTarget() == null
                || request.ecTarget().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "EC 目标值不合法");
        }
        if (request.phTarget() == null
                || request.phTarget().compareTo(BigDecimal.ZERO) <= 0
                || request.phTarget().compareTo(BigDecimal.TEN) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "pH 目标值不合法");
        }
        if (request.zoneName() == null || request.zoneName().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "灌溉分区不能为空");
        }
        if (request.plannedAt() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划时间不能为空");
        }
        if (request.waterVolume() == null
                || request.waterVolume().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划水量不合法");
        }
        if (request.operatorName() == null
                || request.operatorName().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "创建人不能为空");
        }
    }

    private WaterFertilizerMapper.GreenhouseRow requireGreenhouse(
            String greenhouseCode
    ) {
        WaterFertilizerMapper.GreenhouseRow greenhouse =
                mapper.findGreenhouseByCode(greenhouseCode);
        if (greenhouse == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "温室不存在");
        }
        return greenhouse;
    }

    private String nextPlanNo(LocalDate planDate) {
        String prefix = "WF-" + planDate.format(PLAN_DATE_FORMAT) + "-";
        int sequence = mapper.countByPlanNoPrefix(prefix + "%") + 1;
        String planNo;
        do {
            planNo = prefix + String.format("%03d", sequence);
            sequence++;
        } while (mapper.findByPlanNo(planNo) != null);
        return planNo;
    }

    private WaterFertilizerPlanView toView(
            WaterFertilizerMapper.PlanRow plan
    ) {
        return new WaterFertilizerPlanView(
                plan.planId(),
                plan.planNo(),
                plan.greenhouseCode(),
                plan.batchId(),
                plan.cropName(),
                plan.recipeCode(),
                plan.recipeVersion(),
                plan.ecTarget(),
                plan.phTarget(),
                plan.zoneName(),
                plan.plannedAt(),
                plan.waterVolume(),
                plan.status(),
                plan.operatorName(),
                plan.executedAt(),
                plan.failureReason()
        );
    }
}
