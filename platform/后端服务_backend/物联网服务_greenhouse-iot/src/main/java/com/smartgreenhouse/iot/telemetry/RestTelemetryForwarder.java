package com.smartgreenhouse.iot.telemetry;

import com.smartgreenhouse.iot.business.BusinessApiClient;
import org.springframework.stereotype.Component;

@Component
public class RestTelemetryForwarder implements TelemetryForwarder {

    private final BusinessApiClient businessApiClient;

    public RestTelemetryForwarder(BusinessApiClient businessApiClient) {
        this.businessApiClient = businessApiClient;
    }

    @Override
    public void forward(TelemetryMessage message) {
        businessApiClient.forwardTelemetry(message);
    }
}
