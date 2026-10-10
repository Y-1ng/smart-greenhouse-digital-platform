package com.smartgreenhouse.api.modules.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgreenhouse.api.integration.external.ExternalCallResult;
import com.smartgreenhouse.api.integration.external.ExternalPlatform;
import com.smartgreenhouse.api.integration.external.MarketplaceClient;
import com.smartgreenhouse.api.integration.external.SmsClient;
import com.smartgreenhouse.api.integration.external.WeatherClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 外部平台编排：逐个温室调用、失败按配置重试、结果全部留痕，且失败不抛给业务。
 */
class ExternalSyncServiceTest {

    private final WeatherClient weather = mock(WeatherClient.class);
    private final SmsClient sms = mock(SmsClient.class);
    private final MarketplaceClient marketplace = mock(MarketplaceClient.class);
    private final ExternalSyncLogMapper mapper =
            mock(ExternalSyncLogMapper.class);

    @Test
    void logsSuccessForEachGreenhouse() {
        when(weather.mode()).thenReturn("mock");
        when(weather.fetchForecast(anyString(), any())).thenReturn(
                ExternalCallResult.ok("气象预报获取成功", Map.of("temperature", 22.5))
        );
        when(mapper.findGreenhouseTargets(10)).thenReturn(List.of(
                target("GH-001", "A区"),
                target("GH-002", "B区")
        ));

        ExternalViews.ExternalSyncReport report =
                service(2).sync(ExternalPlatform.WEATHER);

        assertThat(report.platform()).isEqualTo("WEATHER");
        assertThat(report.succeeded()).isEqualTo(2);
        assertThat(report.failed()).isZero();
        verify(mapper, times(2)).insert(argThat(log ->
                "SUCCESS".equals(log.status())
                        && log.attempts() == 1
                        && "mock".equals(log.mode())
        ));
    }

    @Test
    void retriesFailedCallUpToMaxAttempts() {
        when(weather.mode()).thenReturn("mock");
        when(weather.fetchForecast(anyString(), any())).thenReturn(
                ExternalCallResult.failure("气象服务连接超时")
        );
        when(mapper.findGreenhouseTargets(10))
                .thenReturn(List.of(target("GH-001", "A区")));

        ExternalViews.ExternalSyncReport report =
                service(2).sync(ExternalPlatform.WEATHER);

        assertThat(report.succeeded()).isZero();
        assertThat(report.failed()).isEqualTo(1);
        verify(weather, times(2)).fetchForecast("GH-001", "A区");
        verify(mapper).insert(argThat(log ->
                "FAILED".equals(log.status())
                        && log.attempts() == 2
                        && log.message().contains("连接超时")
        ));
    }

    @Test
    void alertSmsFailureDoesNotThrow() {
        when(sms.mode()).thenReturn("mock");
        when(sms.sendAlert(anyString(), anyString())).thenReturn(
                ExternalCallResult.failure("短信网关不可用")
        );

        assertThatCode(() ->
                service(2).sendAlertSms("GH-001", "HIGH", "空气温度 38.5 高于告警上限 32")
        ).doesNotThrowAnyException();

        verify(sms, times(2)).sendAlert(anyString(), anyString());
        verify(mapper).insert(argThat(log ->
                "SMS".equals(log.platform()) && log.attempts() == 2
        ));
    }

    @Test
    void statusReportsModeAndCounts() {
        when(weather.mode()).thenReturn("mock");
        when(sms.mode()).thenReturn("rest");
        when(marketplace.mode()).thenReturn("mock");
        when(mapper.findLatest("WEATHER")).thenReturn(new ExternalSyncLogMapper
                .ExternalLogRow(
                9L,
                "WEATHER",
                "GH-001",
                "SUCCESS",
                1,
                15L,
                "mock",
                "气象预报获取成功",
                "{}",
                LocalDateTime.now()
        ));
        when(mapper.countByStatus("WEATHER", "SUCCESS")).thenReturn(4);
        when(mapper.countByStatus("WEATHER", "FAILED")).thenReturn(1);

        List<ExternalViews.ExternalStatusView> status = service(2).status();

        assertThat(status).hasSize(3);
        ExternalViews.ExternalStatusView weatherStatus = status.stream()
                .filter(item -> "WEATHER".equals(item.platform()))
                .findFirst()
                .orElseThrow();
        assertThat(weatherStatus.mode()).isEqualTo("mock");
        assertThat(weatherStatus.lastStatus()).isEqualTo("SUCCESS");
        assertThat(weatherStatus.successCount()).isEqualTo(4);
        assertThat(weatherStatus.failureCount()).isEqualTo(1);

        ExternalViews.ExternalStatusView smsStatus = status.stream()
                .filter(item -> "SMS".equals(item.platform()))
                .findFirst()
                .orElseThrow();
        assertThat(smsStatus.mode()).isEqualTo("rest");
        assertThat(smsStatus.lastStatus()).isEqualTo("NEVER");
    }

    private ExternalSyncService service(int maxAttempts) {
        return new ExternalSyncService(
                weather,
                sms,
                marketplace,
                mapper,
                new ObjectMapper(),
                maxAttempts,
                10,
                "13800000000"
        );
    }

    private ExternalSyncLogMapper.GreenhouseTarget target(
            String code,
            String location
    ) {
        return new ExternalSyncLogMapper.GreenhouseTarget(code, location);
    }
}
