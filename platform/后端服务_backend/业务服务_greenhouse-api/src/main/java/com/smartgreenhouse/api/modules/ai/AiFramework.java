package com.smartgreenhouse.api.modules.ai;

import java.util.List;

/**
 * AI 总结的分析框架。前端只传 id，提示词与输出要求由后端统一维护，
 * 避免同一个框架在两个地方各写一套说法。
 */
public enum AiFramework {

    PRODUCTION(
            "production",
            "农业生产模型",
            "环境偏离、设备回执、水肥执行",
            List.of(
                    "先给当前生产链路的整体判断，再说偏差",
                    "环境指标要与阈值区间对比，指出偏离最大的那几项",
                    "涉及设备或水肥时，必须核对执行回执，指令与现场状态不一致要单独点出来"
            )
    ),
    QUALITY(
            "quality",
            "质量链审计",
            "证据完整性、证书有效性、断链与重复扫码",
            List.of(
                    "按采样、周期、证书、扫码、链上回执的顺序检查证据是否完整",
                    "重点关注待补偿、待确认和断链记录",
                    "证书有效期、重复扫码、编码重复三类异常要分别说明"
            )
    ),
    OPERATION(
            "operation",
            "经营运营模型",
            "履约率、收益波动、分红兑现",
            List.of(
                    "从村庄覆盖、合同订单、股权结构、分红台账四个层面归纳",
                    "指出收益波动最大的主体，并联动看履约率与分红兑现",
                    "按投入、产出、分配三段式给出统一口径的指标建议"
            )
    ),
    GOVERNANCE(
            "governance",
            "风险治理模型",
            "预警闭环、指标口径、外部同步",
            List.of(
                    "围绕监管指标、风险预警、产业带动和外部平台同步形成结论",
                    "未闭环预警和同步失败记录优先级最高",
                    "跨村或跨棚比较时要说明时间窗口是否一致"
            )
    );

    private final String id;
    private final String label;
    private final String focus;
    private final List<String> emphasis;

    AiFramework(String id, String label, String focus, List<String> emphasis) {
        this.id = id;
        this.label = label;
        this.focus = focus;
        this.emphasis = emphasis;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String focus() {
        return focus;
    }

    public List<String> emphasis() {
        return emphasis;
    }

    /** 未知 id 一律按农业生产模型处理，保证接口不会因为前端多传一个值就报错。 */
    public static AiFramework from(String id) {
        if (id == null || id.isBlank()) {
            return PRODUCTION;
        }
        for (AiFramework framework : values()) {
            if (framework.id.equalsIgnoreCase(id.trim())) {
                return framework;
            }
        }
        return PRODUCTION;
    }
}
