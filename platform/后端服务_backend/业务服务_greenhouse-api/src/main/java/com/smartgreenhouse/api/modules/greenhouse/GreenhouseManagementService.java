package com.smartgreenhouse.api.modules.greenhouse;

import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class GreenhouseManagementService {

    private static final int DEFAULT_GROWING_DAYS = 120;

    private final GreenhouseManagementMapper mapper;

    public GreenhouseManagementService(GreenhouseManagementMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public GreenhouseSummary create(CreateGreenhouseCommand command) {
        String code = normalizeCode(command.code());
        String name = normalizeText(command.name(), "温室名称不能为空");
        BigDecimal area = requirePositiveArea(command.area());
        String location = command.location() == null
                || command.location().isBlank()
                ? "西北示范基地"
                : command.location().trim();
        LocalDate plantingDate = command.plantingDate() == null
                ? LocalDate.now()
                : command.plantingDate();
        LocalDate expectedHarvestDate = command.expectedHarvestDate() == null
                ? plantingDate.plusDays(DEFAULT_GROWING_DAYS)
                : command.expectedHarvestDate();
        validateDates(plantingDate, expectedHarvestDate);

        if (mapper.countByCode(code) > 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "温室编号已存在：" + code
            );
        }

        mapper.insertGreenhouse(code, name, area, location);
        GreenhouseSummary greenhouse = mapper.findSummaryByCode(code);
        if (greenhouse == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "温室创建后无法读取"
            );
        }
        insertCrop(
                greenhouse.greenhouseId(),
                command.cropName(),
                command.varietyName(),
                plantingDate,
                expectedHarvestDate
        );
        return mapper.findSummaryByCode(code);
    }

    @Transactional
    public GreenhouseSummary updateCrop(
            String greenhouseCode,
            CropCommand command
    ) {
        String code = normalizeCode(greenhouseCode);
        GreenhouseSummary greenhouse = mapper.findSummaryByCode(code);
        if (greenhouse == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "温室不存在");
        }
        if (mapper.countProtectedTraceData(greenhouse.greenhouseId()) > 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "该温室作物已关联检测报告、合格证或溯源码，不能直接改种"
            );
        }

        String cropName = normalizeText(
                command.cropName(),
                "作物名称不能为空"
        );
        String varietyName = normalizeVariety(command.varietyName());
        LocalDate plantingDate = command.plantingDate() == null
                ? LocalDate.now()
                : command.plantingDate();
        LocalDate expectedHarvestDate = command.expectedHarvestDate() == null
                ? plantingDate.plusDays(DEFAULT_GROWING_DAYS)
                : command.expectedHarvestDate();
        validateDates(plantingDate, expectedHarvestDate);

        Long batchId = mapper.findLatestBatchId(greenhouse.greenhouseId());
        if (batchId == null) {
            insertCrop(
                    greenhouse.greenhouseId(),
                    cropName,
                    varietyName,
                    plantingDate,
                    expectedHarvestDate
            );
        } else {
            mapper.updateCropBatch(
                    batchId,
                    cropName,
                    varietyName,
                    plantingDate,
                    expectedHarvestDate
            );
        }
        return mapper.findSummaryByCode(code);
    }

    @Transactional
    public void delete(String greenhouseCode) {
        String code = normalizeCode(greenhouseCode);
        GreenhouseSummary greenhouse = mapper.findSummaryByCode(code);
        if (greenhouse == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "温室不存在");
        }

        long protectedData =
                mapper.countProtectedOperationalData(greenhouse.greenhouseId())
                + mapper.countProtectedTraceData(greenhouse.greenhouseId());
        if (protectedData > 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "该温室已有关联设备、环境周期或生产数据，不能直接删除"
            );
        }

        mapper.deleteThresholds(greenhouse.greenhouseId());
        mapper.deleteCropBatches(greenhouse.greenhouseId());
        mapper.deleteGreenhouse(code);
    }

    private void insertCrop(
            Long greenhouseId,
            String cropName,
            String varietyName,
            LocalDate plantingDate,
            LocalDate expectedHarvestDate
    ) {
        mapper.insertCropBatch(
                greenhouseId,
                normalizeText(cropName, "作物名称不能为空"),
                normalizeVariety(varietyName),
                plantingDate,
                expectedHarvestDate
        );
    }

    private String normalizeCode(String value) {
        String code = normalizeText(value, "温室编号不能为空")
                .toUpperCase();
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{1,63}")) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "温室编号只能包含字母、数字、横线和下划线"
            );
        }
        return code;
    }

    private BigDecimal requirePositiveArea(BigDecimal area) {
        if (area == null || area.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "温室面积必须大于 0"
            );
        }
        return area;
    }

    private void validateDates(
            LocalDate plantingDate,
            LocalDate expectedHarvestDate
    ) {
        if (expectedHarvestDate.isBefore(plantingDate)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "预计采收日期不能早于种植日期"
            );
        }
    }

    private String normalizeText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String normalizeVariety(String value) {
        return value == null || value.isBlank()
                ? "常规品种"
                : value.trim();
    }

    public record CreateGreenhouseCommand(
            String code,
            String name,
            BigDecimal area,
            String location,
            String cropName,
            String varietyName,
            LocalDate plantingDate,
            LocalDate expectedHarvestDate
    ) {
    }

    public record CropCommand(
            String cropName,
            String varietyName,
            LocalDate plantingDate,
            LocalDate expectedHarvestDate
    ) {
    }
}
