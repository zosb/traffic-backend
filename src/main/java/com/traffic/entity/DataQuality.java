package com.traffic.entity;
import lombok.Data;

@Data
public class DataQuality {
    private String metricName;
    private Double value;
    private String status;
}