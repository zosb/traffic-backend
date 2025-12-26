package com.traffic.mapper;

import com.traffic.entity.RealTimeTraffic;
import com.traffic.entity.RoadPlanning;
import com.traffic.entity.TrafficMonitor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface TrafficMapper {

    // ==================== 🟢 新增：基于真实 Hive/DB 数据的统计接口 ====================

    // 1. 获取数据库中最新的日期 (避免页面写死日期)
    @Select("SELECT MAX(dt) FROM app_traffic_monitor")
    String getMaxDate();

    // 2. 统计某天实际入库的总条数 (用于计算 ODS 缺失率)
    @Select("SELECT COUNT(*) FROM app_traffic_monitor WHERE dt = #{date}")
    Integer getDailyRecordCount(String date);

    // 3. 统计全网路段总数 (用于计算理论应有数据量)
    @Select("SELECT COUNT(DISTINCT road_name) FROM app_traffic_monitor")
    Integer getAllRoadCount();

    // 4. 统计有效 GPS 数据量 (速度 > 0 的视为有效)
    @Select("SELECT COUNT(*) FROM app_traffic_monitor WHERE dt = #{date} AND avg_speed > 0")
    Integer getValidSpeedCount(String date);

    // 5. 真实查找故障设备 (逻辑：某路段全天采集数据少于 12 条，视为设备故障)
    @Select("SELECT road_name as roadName, COUNT(*) as captureCount, road_id as deviceId " +
            "FROM app_traffic_monitor " +
            "WHERE dt = #{date} " +
            "GROUP BY road_name, road_id " +
            "HAVING captureCount < 12 " +
            "ORDER BY captureCount ASC LIMIT 20")
    List<Map<String, Object>> getFaultyDevices(String date);

    // 6. 真实查询最近 7 天的质量趋势 (按日期聚合)
    @Select("SELECT dt as date, " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN avg_speed > 0 THEN 1 ELSE 0 END) as valid " +
            "FROM app_traffic_monitor " +
            "GROUP BY dt " +
            "ORDER BY dt DESC LIMIT 7")
    List<Map<String, Object>> getQualityTrend7Days();

    // ==================== 原有业务接口 (保持不变) ====================

    @Select("SELECT road_name, ROUND(AVG(avg_speed), 1) as avg_speed " +
            "FROM app_traffic_monitor " +
            "WHERE dt = #{date} AND hr = #{hour} " +
            "AND road_name IS NOT NULL AND road_name != '' " +
            "GROUP BY road_name " +
            "ORDER BY avg_speed ASC " +
            "LIMIT 20")
    List<TrafficMonitor> selectRealTimeCongestion(@Param("date") String date, @Param("hour") Integer hour);

    @Select("SELECT t.*, r.geometry_wkt " +
            "FROM app_traffic_monitor t " +
            "JOIN dim_road_info r ON t.road_id = r.road_id " +
            "WHERE t.dt = #{date} AND t.hr = #{hour} " +
            "AND t.real_flow > 0 " +
            "AND r.geometry_wkt IS NOT NULL " +
            "AND t.road_name IS NOT NULL AND t.road_name != '' " +
            "LIMIT 5000")
    List<TrafficMonitor> selectHeatMapData(@Param("date") String date, @Param("hour") Integer hour);

    @Select("SELECT * FROM app_road_planning " +
            "WHERE dt = #{date} AND max_saturation > 1.0 " +
            "AND road_name IS NOT NULL AND road_name != '' " +
            "ORDER BY max_saturation DESC LIMIT 20")
    List<RoadPlanning> selectOverloadedRoads(@Param("date") String date);

    @Select("SELECT t.region_name AS regionName, SUM(t.real_flow) AS totalFlow " +
            "FROM (" +
            "  SELECT " +
            "    CASE " +
            "      WHEN road_name LIKE '%东%' THEN '东部新区' " +
            "      WHEN road_name LIKE '%西%' THEN '西部老城' " +
            "      WHEN road_name LIKE '%南%' THEN '南部物流区' " +
            "      WHEN road_name LIKE '%北%' THEN '北部科技园' " +
            "      WHEN road_name LIKE '%环%' THEN '环路快速网' " +
            "      ELSE '中心城区' " +
            "    END AS region_name, " +
            "    real_flow " +
            "  FROM app_traffic_monitor " +
            "  WHERE dt = #{date} " +
            "  AND road_name IS NOT NULL AND road_name != '' " +
            ") t " +
            "GROUP BY t.region_name")
    List<Map<String, Object>> selectRegionalDistribution(@Param("date") String date);

    @Select("SELECT * FROM app_traffic_monitor " +
            "WHERE dt = #{date} " +
            "AND road_name LIKE CONCAT('%', #{roadName}, '%') " +
            "AND road_name IS NOT NULL AND road_name != '' " +
            "ORDER BY hr ASC")
    List<TrafficMonitor> selectRoadTrend(@Param("date") String date, @Param("roadName") String roadName);

    @Select("SELECT COUNT(*) FROM dim_road_info")
    Integer getTotalRoadCount();

    @Select("SELECT COUNT(DISTINCT road_id) FROM app_traffic_monitor WHERE dt = #{date}")
    Integer getActiveRoadCount(@Param("date") String date);

    @Select("SELECT COUNT(DISTINCT hr) FROM app_traffic_monitor WHERE dt = #{date}")
    Integer getDataHoursCount(@Param("date") String date);

    @Select("SELECT COUNT(*) FROM app_traffic_monitor WHERE dt = #{date}")
    Integer getTotalRecordCount(@Param("date") String date);

    @Select("SELECT " +
            "  r.road_name as roadName, " +
            "  SUM(t.vehicle_count) as vehicleCount, " +
            "  ROUND(AVG(t.avg_speed), 1) as avgSpeed, " +
            "  MAX(t.window_end) as windowEnd " +
            "FROM app_realtime_road_traffic t " +
            "LEFT JOIN dim_road_info r ON t.road_id = r.road_id " +
            "WHERE t.window_end = (SELECT MAX(window_end) FROM app_realtime_road_traffic) " +
            "AND r.road_name IS NOT NULL " +
            "GROUP BY r.road_name " +
            "ORDER BY r.road_name ASC")
    List<RealTimeTraffic> selectLatestRealTimeTraffic();
}