package com.traffic.controller;

import com.traffic.entity.DataQuality;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traffic.entity.RealTimeTraffic;
import com.traffic.entity.RoadPlanning;
import com.traffic.entity.TrafficMonitor;
import com.traffic.mapper.TrafficMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class TrafficController {

    @Autowired
    private TrafficMapper trafficMapper;

    // ==================== ⭐ 新增：统一的 AI 大模型代理接口 ====================
    // 让前端 TrafficManager 将数据发到这里，Java再去请求 Python 5000 端口
    @PostMapping("/monitor/ai_analyze")
    public Map<String, Object> getAiComprehensiveAnalysis(@RequestBody Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonInputString = mapper.writeValueAsString(payload);

            // 请求你服务器上 Python 的 5000 端口
            java.net.URL url = new java.net.URL("http://192.168.10.101:5000/api/ai/comprehensive_analysis");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setDoOutput(true);

            // 写入参数
            try (java.io.OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // 读取 Python AI 返回的结果
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                // 解析返回的 JSON 并直接返给前端
                Map<String, Object> resultMap = mapper.readValue(response.toString(), Map.class);
                result.put("status", resultMap.get("status"));
                result.put("report", resultMap.get("report"));
                return result;
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("error", "AI 连接失败: " + e.getMessage());
            result.put("report", "<div style='color:#F56C6C; background:#fef0f0; padding:15px; border-radius:5px; border-left:4px solid #F56C6C;'><i class='el-icon-circle-close'></i> <b>Java后端无法连接到Python AI引擎(192.168.10.101:5000)</b><br/>请确保服务器 Python 服务已启动。</div>");
            return result;
        }
    }

    // ==================== 1. 真实质量管控报告接口 ====================
    @GetMapping("/analysis/quality_report")
    public Map<String, Object> getQualityReport() {
        Map<String, Object> report = new HashMap<>();

        String latestDate = trafficMapper.getMaxDate();
        if (latestDate == null) latestDate = "2025-11-04";
        report.put("date", latestDate);

        Integer actualCount = trafficMapper.getDailyRecordCount(latestDate);
        Integer totalRoads = trafficMapper.getAllRoadCount();
        if (totalRoads == null || totalRoads == 0) totalRoads = 1;
        int theoreticalCount = totalRoads * 24;

        double missingRate = 0.0;
        if (theoreticalCount > 0 && actualCount != null) {
            missingRate = (double) (theoreticalCount - actualCount) / theoreticalCount * 100;
        }
        if (missingRate < 0) missingRate = 0.0;
        report.put("odsValue", String.format("%.2f", missingRate));

        Integer validSpeedCount = trafficMapper.getValidSpeedCount(latestDate);
        double gpsAccuracy = 0.0;
        if (actualCount != null && actualCount > 0) {
            gpsAccuracy = (double) validSpeedCount / actualCount * 100;
        }
        report.put("gpsValue", String.format("%.2f", gpsAccuracy));
        report.put("faultyDevices", trafficMapper.getFaultyDevices(latestDate));

        List<Map<String, Object>> trendList = trafficMapper.getQualityTrend7Days();
        List<String> dates = new ArrayList<>();
        List<Double> odsTrend = new ArrayList<>();
        List<Double> gpsTrend = new ArrayList<>();

        for (int i = trendList.size() - 1; i >= 0; i--) {
            Map<String, Object> row = trendList.get(i);
            dates.add(row.get("date").toString());
            long total = Long.parseLong(row.get("total").toString());
            long valid = Long.parseLong(row.get("valid").toString());
            double g = total > 0 ? (double) valid / total * 100 : 0;
            gpsTrend.add(Double.parseDouble(String.format("%.1f", g)));
            double o = total > 0 ? Math.max(0, (double)(theoreticalCount - total) / theoreticalCount * 100) : 100;
            odsTrend.add(Double.parseDouble(String.format("%.1f", o)));
        }

        Map<String, Object> trendMap = new HashMap<>();
        trendMap.put("dates", dates);
        trendMap.put("ods", odsTrend);
        trendMap.put("gps", gpsTrend);
        report.put("trend", trendMap);

        return report;
    }

    // ==================== 2. 真实流量趋势对比接口 ====================
    @GetMapping("/analysis/trend_compare")
    public Map<String, Object> getTrendComparison(@RequestParam String date, @RequestParam String roadName) {
        Map<String, Object> result = new HashMap<>();

        List<TrafficMonitor> todayData = trafficMapper.selectRoadTrend(date, roadName);
        if (todayData == null || todayData.isEmpty()) {
            String latest = trafficMapper.getMaxDate();
            if (latest != null) {
                todayData = trafficMapper.selectRoadTrend(latest, roadName);
                date = latest;
            }
        }

        String historyDate = null;
        try {
            LocalDate d = LocalDate.parse(date);
            historyDate = d.minusDays(7).toString();
        } catch (Exception e) {
            historyDate = "2025-11-04";
        }

        List<TrafficMonitor> historyData = trafficMapper.selectRoadTrend(historyDate, roadName);
        if (historyData == null || historyData.isEmpty()) {
            try {
                LocalDate d = LocalDate.parse(date);
                historyDate = d.minusDays(1).toString();
                historyData = trafficMapper.selectRoadTrend(historyDate, roadName);
            } catch (Exception e) {}
        }

        List<Integer> historyFlow = new ArrayList<>();
        List<String> hours = new ArrayList<>();

        if (todayData != null) {
            for (TrafficMonitor item : todayData) hours.add(item.getHr() + "点");
        }

        if (historyData != null && !historyData.isEmpty()) {
            Map<Integer, Integer> historyMap = new HashMap<>();
            for(TrafficMonitor m : historyData) historyMap.put(m.getHr(), m.getRealFlow());
            if (todayData != null) {
                for (TrafficMonitor item : todayData) historyFlow.add(historyMap.getOrDefault(item.getHr(), 0));
            }
        } else {
            if (todayData != null) {
                for (int i = 0; i < todayData.size(); i++) historyFlow.add(0);
            }
        }

        result.put("hours", hours);
        result.put("todayFlow", todayData != null ? todayData.stream().map(TrafficMonitor::getRealFlow).toArray() : new int[0]);
        result.put("historyFlow", historyFlow);
        result.put("todaySpeed", todayData != null ? todayData.stream().map(TrafficMonitor::getAvgSpeed).toArray() : new int[0]);
        result.put("insight", generateInsight(roadName, todayData != null ? todayData : new ArrayList<>()));

        return result;
    }

    // ==================== 原有其他接口 (保持不变) ====================
    @GetMapping("/monitor/rank")
    public List<TrafficMonitor> getCongestionRanking(@RequestParam String date, @RequestParam Integer hour) {
        return trafficMapper.selectRealTimeCongestion(date, hour);
    }

    @GetMapping("/monitor/heatmap")
    public List<TrafficMonitor> getHeatMap(@RequestParam String date, @RequestParam Integer hour) {
        return trafficMapper.selectHeatMapData(date, hour);
    }

    @GetMapping("/planning/advice")
    public List<RoadPlanning> getPlanningAdvice(@RequestParam String date) {
        return trafficMapper.selectOverloadedRoads(date);
    }

    @GetMapping("/planning/region")
    public List<Map<String, Object>> getRegionalStats(@RequestParam String date) {
        return trafficMapper.selectRegionalDistribution(date);
    }

    @GetMapping("/analysis/trend")
    public List<TrafficMonitor> getRoadTrend(@RequestParam String date, @RequestParam String roadName) {
        return trafficMapper.selectRoadTrend(date, roadName);
    }

    @GetMapping("/monitor/realtime_list")
    public List<RealTimeTraffic> getRealTimeList() {
        return trafficMapper.selectLatestRealTimeTraffic();
    }

    @GetMapping("/analysis/quality")
    public List<DataQuality> getDataQuality() {
        String date = "2025-11-04";
        List<DataQuality> list = new ArrayList<>();
        Integer totalRoads = trafficMapper.getTotalRoadCount();
        Integer activeRoads = trafficMapper.getActiveRoadCount(date);
        list.add(buildQualityMetric("路网覆盖率(%)", totalRoads, activeRoads, 10.0, 100.0));
        Integer dataHours = trafficMapper.getDataHoursCount(date);
        list.add(buildQualityMetric("时间完整性(%)", 24, dataHours, 95.0, 100.0));
        Integer totalRecords = trafficMapper.getTotalRecordCount(date);
        DataQuality q3 = new DataQuality();
        q3.setMetricName("有效记录数(万)");
        if (totalRecords != null) {
            q3.setValue(Double.parseDouble(String.format("%.2f", totalRecords / 10000.0)));
            q3.setStatus(totalRecords > 20000 ? "充足" : "一般");
        } else { q3.setValue(0.0); q3.setStatus("无数据"); }
        list.add(q3);
        return list;
    }

    private DataQuality buildQualityMetric(String name, Integer den, Integer num, Double threshold, Double mult) {
        DataQuality q = new DataQuality();
        q.setMetricName(name);
        if (den != null && den > 0 && num != null) {
            double val = (double) num / den * mult;
            q.setValue(Double.parseDouble(String.format("%.2f", val)));
            q.setStatus(val >= threshold ? "优秀" : "偏低");
        } else { q.setValue(0.0); q.setStatus("异常"); }
        return q;
    }

    // ⭐ 修复了原代码中 os.getOutputStream() 重复写入导致报错的Bug
    private String generateInsight(String roadName, List<TrafficMonitor> data) {
        if (data == null || data.isEmpty()) return "暂无数据进行分析";

        TrafficMonitor peak = data.get(0);
        for (TrafficMonitor m : data) {
            if (m.getRealFlow() > peak.getRealFlow()) peak = m;
        }

        try {
            String jsonInputString = String.format(
                    "{\"roadName\": \"%s\", \"hr\": %d, \"flow\": %d, \"speed\": %.2f, \"role\":\"analyst\"}",
                    roadName, peak.getHr(), peak.getRealFlow(), peak.getAvgSpeed()
            );

            // 此处兼容 DataAnalyst，指向最新的大模型接口
            java.net.URL url = new java.net.URL("http://192.168.10.101:5000/api/ai/comprehensive_analysis");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setDoOutput(true);

            // 写入请求体
            try (java.io.OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // 读取返回结果 (原代码这里有复制粘贴错误，已修复)
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> resultMap = mapper.readValue(response.toString(), Map.class);
                if (resultMap.containsKey("report")) {
                    return (String) resultMap.get("report");
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ AI 服务调用异常: " + e.getMessage());
        }
        return fallbackInsight(roadName, peak);
    }

    private String fallbackInsight(String roadName, TrafficMonitor peak) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p style='color:gray'>(AI 服务未连接，使用规则引擎分析)</p>");
        sb.append("<p>📊 <strong>").append(roadName).append(" 简报：</strong></p>");
        sb.append("今日峰值出现在 <strong>").append(peak.getHr()).append(":00</strong>，流量 ").append(peak.getRealFlow()).append(" pcu/h。");
        return sb.toString();
    }

    @GetMapping("/analysis/period_compare")
    public List<Map<String, Object>> getPeriodCompare(@RequestParam String date) {
        return trafficMapper.selectPeriodCompare(date);
    }

    // ==================== ⭐ 面向交通规划师的大模型批处理代理 ====================
    @PostMapping("/planning/ai_batch_analyze")
    public Map<String, Object> getPlanningAiBatchAnalyze(@RequestBody Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonInputString = mapper.writeValueAsString(payload);

            // 请求 Python 端的批量规划分析接口
            java.net.URL url = new java.net.URL("http://192.168.10.101:5000/api/ai/planning_batch_analysis");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setDoOutput(true);

            try (java.io.OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                // 直接将 Python 生成的超级 JSON 原封不动返回给前端 Vue
                return mapper.readValue(response.toString(), Map.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("error", "AI 批处理研判连接失败: " + e.getMessage());
            return result;
        }
    }
}