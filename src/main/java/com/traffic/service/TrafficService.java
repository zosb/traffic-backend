package com.traffic.service;

import com.traffic.entity.RoadPlanning;
import com.traffic.entity.TrafficMonitor;
import java.util.List;

public interface TrafficService {
    List<TrafficMonitor> getCongestionRanking(String date, Integer hour);
    List<TrafficMonitor> getHeatMap(String date, Integer hour);
    List<RoadPlanning> getPlanningSuggestions(String date);
    List<TrafficMonitor> getTrendAnalysis(String date, String roadName);
}