package com.traffic.entity;

import lombok.Data;

@Data
public class TrafficMonitor {
    private String dt;
    private String roadId;
    private String roadName;
    private Integer hr;
    private Integer realFlow;
    private Double avgSpeed;
    private String congestionStatus;
    private String geometryWkt;
}