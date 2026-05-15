package com.traffic.mapper;

import com.traffic.entity.RealTimeTraffic;
import com.traffic.entity.RoadPlanning;
import com.traffic.entity.TrafficMonitor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;
import com.traffic.entity.User;
import com.traffic.entity.DeviceWorkOrder;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface TrafficMapper {
    @Select("SELECT MAX(dt) FROM dm_road_flow_summary")
    String getMaxDate();

    @Select("SELECT COUNT(*) FROM dm_road_flow_summary WHERE dt = #{date}")
    Integer getDailyRecordCount(String date);

    @Select("SELECT COUNT(DISTINCT road_name) FROM dm_road_flow_summary")
    Integer getAllRoadCount();

    @Select("SELECT COUNT(*) FROM dm_road_flow_summary WHERE dt = #{date} AND avg_speed > 0")
    Integer getValidSpeedCount(String date);

    @Select("SELECT road_name as roadName, COUNT(*) as captureCount, road_id as deviceId " +
            "FROM dm_road_flow_summary " +
            "WHERE dt = #{date} " +
            "GROUP BY road_name, road_id " +
            "HAVING captureCount <12 " +
            "ORDER BY captureCount ASC LIMIT 50")
    List<Map<String, Object>> getFaultyDevices(String date);

    @Select("SELECT dt as date, " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN avg_speed > 0 THEN 1 ELSE 0 END) as valid " +
            "FROM dm_road_flow_summary " +
            "GROUP BY dt " +
            "ORDER BY dt DESC LIMIT 7")
    List<Map<String, Object>> getQualityTrend7Days();

    @Select("SELECT road_name, ROUND(AVG(avg_speed), 1) as avg_speed " +
            "FROM dm_road_flow_summary " +
            "WHERE dt = #{date} AND hr = #{hour} " +
            "AND road_name IS NOT NULL AND road_name != '' " +
            "GROUP BY road_name " +
            "ORDER BY avg_speed ASC " +
            "LIMIT 20")
    List<TrafficMonitor> selectRealTimeCongestion(@Param("date") String date, @Param("hour") Integer hour);

    @Select("SELECT t.*, r.geometry_wkt " +
            "FROM dm_road_flow_summary t " +
            "JOIN dim_road_info r ON t.road_id = r.road_id " +
            "WHERE t.dt = #{date} AND t.hr = #{hour} " +
            "AND t.real_flow > 0 " +
            "AND r.geometry_wkt IS NOT NULL " +
            "AND t.road_name IS NOT NULL AND t.road_name != '' " +
            "LIMIT 5000")
    List<TrafficMonitor> selectHeatMapData(@Param("date") String date, @Param("hour") Integer hour);

    @Select("SELECT * FROM dm_congestion_result " +
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
            "  FROM dm_road_flow_summary " +
            "  WHERE dt = #{date} " +
            "  AND road_name IS NOT NULL AND road_name != '' " +
            ") t " +
            "GROUP BY t.region_name")
    List<Map<String, Object>> selectRegionalDistribution(@Param("date") String date);

    @Select("SELECT * FROM dm_road_flow_summary " +
            "WHERE dt = #{date} " +
            "AND road_name LIKE CONCAT('%', #{roadName}, '%') " +
            "AND road_name IS NOT NULL AND road_name != '' " +
            "ORDER BY hr ASC")
    List<TrafficMonitor> selectRoadTrend(@Param("date") String date, @Param("roadName") String roadName);

    @Select("SELECT COUNT(*) FROM dim_road_info")
    Integer getTotalRoadCount();

    @Select("SELECT COUNT(DISTINCT road_id) FROM dm_road_flow_summary WHERE dt = #{date}")
    Integer getActiveRoadCount(@Param("date") String date);

    @Select("SELECT COUNT(DISTINCT hr) FROM dm_road_flow_summary WHERE dt = #{date}")
    Integer getDataHoursCount(@Param("date") String date);

    @Select("SELECT COUNT(*) FROM dm_road_flow_summary WHERE dt = #{date}")
    Integer getTotalRecordCount(@Param("date") String date);

    @Select("SELECT " +
            "  r.road_name as roadName, " +
            "  SUM(t.vehicle_count) as vehicleCount, " +
            "  ROUND(AVG(t.avg_speed), 1) as avgSpeed, " +
            "  MAX(t.window_end) as windowEnd " +
            "FROM dm_trend_window t " +
            "LEFT JOIN dim_road_info r ON t.road_id = r.road_id " +
            "WHERE t.window_end = (SELECT MAX(window_end) FROM dm_trend_window) " +
            "AND r.road_name IS NOT NULL " +
            "GROUP BY r.road_name " +
            "ORDER BY r.road_name ASC")
    List<RealTimeTraffic> selectLatestRealTimeTraffic();

    @Select("SELECT road_name AS roadName, " +
            "SUM(CASE WHEN hr >= 7 AND hr <= 9 THEN real_flow ELSE 0 END) AS morningFlow, " +
            "SUM(CASE WHEN hr > 9 AND hr < 17 THEN real_flow ELSE 0 END) AS flatFlow, " +
            "SUM(CASE WHEN hr >= 17 AND hr <= 19 THEN real_flow ELSE 0 END) AS eveningFlow " +
            "FROM dm_road_flow_summary " +
            "WHERE dt = #{date} AND road_name IS NOT NULL AND road_name != '' " +
            "GROUP BY road_name " +
            "ORDER BY (morningFlow + eveningFlow) DESC LIMIT 5")
    List<Map<String, Object>> selectPeriodCompare(String date);

    @Select("SELECT user_id as userId, username, password, real_name as realName, role_code as roleCode " +
            "FROM sys_user WHERE username = #{username} AND password = #{password} AND role_code = #{role}")
    User login(@Param("username") String username, @Param("password") String password, @Param("role") String role);

    @Insert("INSERT INTO app_ai_report_history (report_id, target_date, road_name, trigger_role, ai_insight_text) " +
            "VALUES (#{id}, #{date}, #{road}, #{role}, #{text})")
    void saveAiReport(@Param("id") String id, @Param("date") String date,
                      @Param("road") String road, @Param("role") String role, @Param("text") String text);

    @Insert("INSERT INTO app_device_work_order (order_id, device_id, fault_type, status, dispatcher) " +
            "VALUES (#{orderId}, #{deviceId}, #{faultType}, 'PENDING', 'System')")
    void createWorkOrder(DeviceWorkOrder order);
}