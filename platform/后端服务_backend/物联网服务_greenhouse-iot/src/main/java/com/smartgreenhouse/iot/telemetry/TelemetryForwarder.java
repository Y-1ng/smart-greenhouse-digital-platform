package com.smartgreenhouse.iot.telemetry;

public interface TelemetryForwarder {

    void forward(TelemetryMessage message);
}
