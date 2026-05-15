package com.traffic.entity;

public class DeviceWorkOrder {
    private String orderId;
    private String deviceId;
    private String faultType;
    private String status;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getFaultType() { return faultType; }
    public void setFaultType(String faultType) { this.faultType = faultType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}