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
// 注意：这里已经删除了 Random 的引用

@RestController
@RequestMapping("/api")
@CrossOrigin
public class TrafficController {

    @Autowired
    private TrafficMapper trafficMapper;

    // ==================== 1. 真实质量管控报告接口 (无模拟) ====================
    @GetMapping("/analysis/quality_report")
    public Map<String, Object> getQualityReport() {
        Map<String, Object> report = new HashMap<>();

        // 1. 获取最新日期
        String latestDate = trafficMapper.getMaxDate();
        if (latestDate == null) latestDate = "2025-11-04";
        report.put("date", latestDate);

        // 2. ODS 缺失率 (真实计算：理论值 vs 实际值)
        Integer actualCount = trafficMapper.getDailyRecordCount(latestDate);
        Integer totalRoads = trafficMapper.getAllRoadCount();
        if (totalRoads == null || totalRoads == 0) totalRoads = 1;

        // 理论值 = 路段数 * 24小时
        int theoreticalCount = totalRoads * 24;

        double missingRate = 0.0;
        if (theoreticalCount > 0 && actualCount != null) {
            missingRate = (double) (theoreticalCount - actualCount) / theoreticalCount * 100;
        }
        if (missingRate < 0) missingRate = 0.0; // 防止数据超发导致负数
        report.put("odsValue", String.format("%.2f", missingRate));

        // 3. GPS 匹配准确率 (真实计算：有效GPS数 / 总数)
        Integer validSpeedCount = trafficMapper.getValidSpeedCount(latestDate);
        double gpsAccuracy = 0.0;
        if (actualCount != null && actualCount > 0) {
            gpsAccuracy = (double) validSpeedCount / actualCount * 100;
        }
        report.put("gpsValue", String.format("%.2f", gpsAccuracy));

        // 4. 故障设备 (真实查出来的少数据路段)
        report.put("faultyDevices", trafficMapper.getFaultyDevices(latestDate));

        // 5. 趋势图 (真实数据库聚合)
        List<Map<String, Object>> trendList = trafficMapper.getQualityTrend7Days();
        List<String> dates = new ArrayList<>();
        List<Double> odsTrend = new ArrayList<>();
        List<Double> gpsTrend = new ArrayList<>();

        // 倒序处理，保证时间轴从左到右
        for (int i = trendList.size() - 1; i >= 0; i--) {
            Map<String, Object> row = trendList.get(i);
            dates.add(row.get("date").toString());

            long total = Long.parseLong(row.get("total").toString());
            long valid = Long.parseLong(row.get("valid").toString());

            // 计算当天的 GPS 准确率
            double g = total > 0 ? (double) valid / total * 100 : 0;
            gpsTrend.add(Double.parseDouble(String.format("%.1f", g)));

            // 计算当天的 ODS 缺失率
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

    // ==================== 2. 真实流量趋势对比接口 (移除 Random) ====================
    @GetMapping("/analysis/trend_compare")
    public Map<String, Object> getTrendComparison(@RequestParam String date, @RequestParam String roadName) {
        Map<String, Object> result = new HashMap<>();

        // 1. 查“今天”的数据
        List<TrafficMonitor> todayData = trafficMapper.selectRoadTrend(date, roadName);
        // 如果今天没数据，找最新的一天作为基准
        if (todayData == null || todayData.isEmpty()) {
            String latest = trafficMapper.getMaxDate();
            if (latest != null) {
                todayData = trafficMapper.selectRoadTrend(latest, roadName);
                date = latest;
            }
        }

        // 2. 查“历史”数据 (计算7天前的日期)
        String historyDate = null;
        try {
            LocalDate d = LocalDate.parse(date);
            historyDate = d.minusDays(7).toString(); // 往前推 7 天
        } catch (Exception e) {
            historyDate = "2025-11-04"; // 兜底
        }

        // 🟢 关键修改：真的去数据库查历史日期的数据，而不是模拟
        List<TrafficMonitor> historyData = trafficMapper.selectRoadTrend(historyDate, roadName);

        // 如果 7 天前没数据，尝试 1 天前
        if (historyData == null || historyData.isEmpty()) {
            try {
                LocalDate d = LocalDate.parse(date);
                historyDate = d.minusDays(1).toString();
                historyData = trafficMapper.selectRoadTrend(historyDate, roadName);
            } catch (Exception e) {}
        }

        // 3. 组装数据
        List<Integer> historyFlow = new ArrayList<>();
        List<String> hours = new ArrayList<>();

        // 提取小时轴
        if (todayData != null) {
            for (TrafficMonitor item : todayData) {
                hours.add(item.getHr() + "点");
            }
        }

        // 匹配历史流量 (防止数据缺失导致的错位)
        if (historyData != null && !historyData.isEmpty()) {
            Map<Integer, Integer> historyMap = new HashMap<>();
            for(TrafficMonitor m : historyData) {
                historyMap.put(m.getHr(), m.getRealFlow());
            }
            if (todayData != null) {
                for (TrafficMonitor item : todayData) {
                    historyFlow.add(historyMap.getOrDefault(item.getHr(), 0));
                }
            }
        } else {
            // 如果实在查不到历史数据，给 0 (保持诚实，不瞎编)
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

    private String generateInsight(String roadName, List<TrafficMonitor> data) {
        if (data == null || data.isEmpty()) return "暂无数据进行分析";

        // 1. 找到今日流量最大的时刻（峰值点），作为分析对象
        TrafficMonitor peak = data.get(0);
        for (TrafficMonitor m : data) {
            if (m.getRealFlow() > peak.getRealFlow()) peak = m;
        }

        // 2. 尝试调用 Python AI 服务
        try {
            // 构建 JSON 请求体
            String jsonInputString = String.format(
                    "{\"roadName\": \"%s\", \"hr\": %d, \"flow\": %d, \"speed\": %.2f}",
                    roadName, peak.getHr(), peak.getRealFlow(), peak.getAvgSpeed()
            );

            // 发送 HTTP POST 请求到 Python (端口 5000)
            java.net.URL url = new java.net.URL("http://192.168.10.101:5000/predict");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setDoOutput(true);

            try (java.io.OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // 读取 Python 返回的结果
            try (java.io.OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // 🟢 【核心修改】使用 Jackson 自动解析 JSON，再也不用担心截取错误了！
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(con.getInputStream(), "utf-8"))) {

                // 1. 先把返回的数据读成字符串
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                // 2. 用 ObjectMapper 把它转成 Map 对象
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> resultMap = mapper.readValue(response.toString(), Map.class);

                // 3. 精准取出 report 字段
                if (resultMap.containsKey("report")) {
                    return (String) resultMap.get("report");
                }
            }

        } catch (Exception e) {
            System.out.println("⚠️ AI 服务调用异常: " + e.getMessage());
            // 出错时继续走下面的兜底逻辑
        }

        // --- 兜底逻辑 (旧版规则代码，保留作为备用) ---
        return fallbackInsight(roadName, peak);
    }

    // 旧版逻辑改名为 fallbackInsight
    private String fallbackInsight(String roadName, TrafficMonitor peak) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p style='color:gray'>(AI 服务未连接，使用规则引擎分析)</p>");
        sb.append("<p>📊 <strong>").append(roadName).append(" 简报：</strong></p>");
        sb.append("今日峰值出现在 <strong>").append(peak.getHr()).append(":00</strong>，流量 ").append(peak.getRealFlow()).append(" pcu/h。");
        return sb.toString();
    }
}