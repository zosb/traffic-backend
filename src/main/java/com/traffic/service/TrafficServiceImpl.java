package com.traffic.service;

import com.traffic.entity.RoadPlanning;
import com.traffic.entity.TrafficMonitor;
import com.traffic.mapper.TrafficMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TrafficServiceImpl implements TrafficService {

    @Autowired
    private TrafficMapper trafficMapper;

    @Override
    public List<TrafficMonitor> getCongestionRanking(String date, Integer hour) {
        return trafficMapper.selectRealTimeCongestion(date, hour);
    }

    @Override
    public List<TrafficMonitor> getHeatMap(String date, Integer hour) {
        return trafficMapper.selectHeatMapData(date, hour);
    }

    @Override
    public List<RoadPlanning> getPlanningSuggestions(String date) {
        return trafficMapper.selectOverloadedRoads(date);
    }

    @Override
    public List<TrafficMonitor> getTrendAnalysis(String date, String roadName) {
        return trafficMapper.selectRoadTrend(date, roadName);
    }
}