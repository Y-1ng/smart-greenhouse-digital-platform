package com.smartgreenhouse.api.modules.waterfertilizer;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * demo 配置下确保当天始终有水肥计划，便于演示新增和执行流程。
 */
@Component
@Profile("demo")
public class DemoWaterFertilizerInitializer implements ApplicationRunner {

    private final WaterFertilizerService service;
    private final WaterFertilizerMapper mapper;

    public DemoWaterFertilizerInitializer(
            WaterFertilizerService service,
            WaterFertilizerMapper mapper
    ) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureAllDailyPlans();
    }

    @Scheduled(
            fixedDelayString = "${app.demo.water-fertilizer.ensure-interval-ms:60000}",
            initialDelayString = "${app.demo.water-fertilizer.ensure-initial-delay-ms:45000}"
    )
    public synchronized void ensureAllDailyPlans() {
        LocalDate today = LocalDate.now();
        for (WaterFertilizerMapper.GreenhouseRow greenhouse
                : mapper.findAllGreenhouses()) {
            ensureDailyPlans(greenhouse, today);
        }
    }

    private void ensureDailyPlans(
            WaterFertilizerMapper.GreenhouseRow greenhouse,
            LocalDate today
    ) {
        if (!service.list(greenhouse.greenhouseCode(), today).isEmpty()) {
            return;
        }
        WaterFertilizerMapper.BatchRow batch =
                mapper.findFirstBatch(greenhouse.greenhouseId());
        if (batch == null) {
            return;
        }
        PlanProfile profile = profileFor(batch.cropName());

        List<WaterFertilizerPlanView> plans = List.of(
                createPlan(
                        greenhouse.greenhouseCode(),
                        batch.batchId(),
                        profile,
                        "A区 / B区",
                        today,
                        LocalTime.of(11, 30),
                        profile.morningVolume()
                ),
                createPlan(
                        greenhouse.greenhouseCode(),
                        batch.batchId(),
                        profile,
                        "A区",
                        today,
                        LocalTime.of(15, 30),
                        profile.noonVolume()
                ),
                createPlan(
                        greenhouse.greenhouseCode(),
                        batch.batchId(),
                        profile,
                        "B区",
                        today,
                        LocalTime.of(19, 0),
                        profile.eveningVolume()
                )
        );
        service.execute(
                plans.get(0).planNo(),
                new WaterFertilizerController.ExecutePlanRequest(
                        "系统演示",
                        LocalDateTime.now()
                )
        );
    }

    private WaterFertilizerPlanView createPlan(
            String greenhouseCode,
            Long batchId,
            PlanProfile profile,
            String zoneName,
            LocalDate today,
            LocalTime time,
            BigDecimal waterVolume
    ) {
        return service.create(new WaterFertilizerController.CreatePlanRequest(
                greenhouseCode,
                batchId,
                profile.recipeCode(),
                profile.recipeVersion(),
                profile.ecTarget(),
                profile.phTarget(),
                zoneName,
                LocalDateTime.of(today, time),
                waterVolume,
                "系统演示"
        ));
    }

    private PlanProfile profileFor(String cropName) {
        return switch (cropName == null ? "" : cropName) {
            case "草莓" -> new PlanProfile(
                    "STRAWBERRY-FRUIT", "V2",
                    new BigDecimal("1.4"), new BigDecimal("6.0"),
                    new BigDecimal("1.6"), new BigDecimal("1.9"),
                    new BigDecimal("1.3")
            );
            case "黄瓜" -> new PlanProfile(
                    "CUCUMBER-FRUIT", "V3",
                    new BigDecimal("2.0"), new BigDecimal("6.1"),
                    new BigDecimal("2.2"), new BigDecimal("2.6"),
                    new BigDecimal("1.8")
            );
            case "辣椒" -> new PlanProfile(
                    "PEPPER-FRUIT", "V2",
                    new BigDecimal("1.7"), new BigDecimal("6.2"),
                    new BigDecimal("1.9"), new BigDecimal("2.2"),
                    new BigDecimal("1.5")
            );
            case "生菜" -> new PlanProfile(
                    "LETTUCE-LEAF", "V2",
                    new BigDecimal("1.2"), new BigDecimal("6.0"),
                    new BigDecimal("1.2"), new BigDecimal("1.5"),
                    new BigDecimal("1.0")
            );
            default -> new PlanProfile(
                    "TOMATO-FRUIT", "V3",
                    new BigDecimal("1.8"), new BigDecimal("6.2"),
                    new BigDecimal("2.1"), new BigDecimal("2.4"),
                    new BigDecimal("1.6")
            );
        };
    }

    private record PlanProfile(
            String recipeCode,
            String recipeVersion,
            BigDecimal ecTarget,
            BigDecimal phTarget,
            BigDecimal morningVolume,
            BigDecimal noonVolume,
            BigDecimal eveningVolume
    ) {
    }
}
