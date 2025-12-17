package com.traffic.entity;

import lombok.Data;

@Data
public class RealTimeTraffic {
    private String windowEnd;
    private Integer roadId;
    private Integer vehicleCount;
    private Double avgSpeed;
    private String roadName;
}