package com.traffic.entity;

import lombok.Data;

@Data
public class RoadPlanning {
    private String dt;
    private String roadName;
    private Integer lanes;
    private Integer capacity;
    private Integer maxPeakFlow;
    private Double maxSaturation;
    private String planningSuggestion;
}