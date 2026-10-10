package com.smartgreenhouse.api.modules.external;

import com.smartgreenhouse.api.modules.alert.AlertRaisedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 告警短信通知：只在告警事务提交成功后调用外部短信通道，避免"数据库回滚但短信已发出"。
 */
@Component
public class ExternalAlertListener {

    private static final Logger log =
            LoggerFactory.getLogger(ExternalAlertListener.class);

    private final ExternalSyncService service;
    private final ExternalSyncLogMapper mapper;

    public ExternalAlertListener(
            ExternalSyncService service,
            ExternalSyncLogMapper mapper
    ) {
        this.service = service;
        this.mapper = mapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlertRaised(AlertRaisedEvent event) {
        String greenhouseCode =
                mapper.findGreenhouseCode(event.greenhouseId());
        try {
            service.sendAlertSms(
                    greenhouseCode == null
                            ? "GH-" + event.greenhouseId()
                            : greenhouseCode,
                    event.level(),
                    event.message()
            );
        } catch (Exception exception) {
            // 短信属于尽力而为的旁路动作，失败不影响告警与业务事务
            log.warn("预警短信通知失败：{}", exception.getMessage());
        }
    }
}
