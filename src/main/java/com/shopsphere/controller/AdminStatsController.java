// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AdminStatsController 骨架（类声明、注解、字段）
// 人工修改：stats 方法体由 AI 按学员确认补充实现（学员授权直接补全）
// AI 增强：P1-9 新增每日销售额趋势 / 分类销售占比 / 日期筛选 / CSV 导出
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.controller;

import com.shopsphere.common.OrderStatus;
import com.shopsphere.entity.Category;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.DailySalesStat;
import com.shopsphere.repository.CategorySalesStat;
import com.shopsphere.repository.OrderItemRepository;
import com.shopsphere.repository.OrderRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 后台·统计报表（计划书 3.6）：指标卡 / 热销 Top5 / 每日销售额趋势 /
 * 分类销售占比 / 日期筛选 / CSV 导出（P1-9 增强）。
 *
 * <p>[AI骨架] 统计 SQL 已由 AI 在 Repository 里实现完毕（计划书分工：统计 SQL 属 AI）。
 * 你要做的只是"调方法 + 塞 model"——但请先读一遍那两个查询方法的 javadoc，
 * 答辩会问 GROUP BY 和接口投影。
 */
@Controller
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CategoryRepository categoryRepository;

    /**
     * "付过款"的状态集合：已付款 / 已发货 / 已完成。
     * 待付款（没成交）和已取消（退款了）都不算业绩。
     * 统计页的所有指标共用这一个口径——常量抽出来，保证口径统一。
     */
    private static final List<OrderStatus> PAID_STATUSES =
            List.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.COMPLETED);

    /** 趋势图最多渲染 90 天（条形图一行一天，再多页面就没法看了）。 */
    private static final int MAX_RANGE_DAYS = 90;

    /** 百分比换算常数（BigDecimal.multiply 不接受 int 字面量）。 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * 每日销售额一行（条形图 + CSV 共用同一份数据源，口径必然一致）。
     */
    @Data
    @AllArgsConstructor
    public static class DailySalesBar {
        private LocalDate date;
        /** 当日销售额；没有成交则为 0（补零，图上不能缺日期）。 */
        private BigDecimal sales;
        private Long orderCount;
        /** 条形宽度百分比（当日销售额 / 区间最大日销售额 × 100）。 */
        private int widthPercent;
    }

    /**
     * 分类占比一行。
     */
    @Data
    @AllArgsConstructor
    public static class CategoryShare {
        /** 分类名（分类后来被删了则显示「分类#id」兜底）。 */
        private String name;
        private BigDecimal sales;
        /** 占已付款总销售额的百分比（保留 1 位小数）。 */
        private double percent;
        /** 条形宽度百分比（按分类销售额最大值归一）。 */
        private int widthPercent;
        private Long totalQty;
    }

    /**
     * 统计页 GET /admin/stats（支持 ?range=7|30 或 ?from=yyyy-MM-dd&to=yyyy-MM-dd）。
     *
     * model：
     *   原有四样 —— orderCount / paidCount / totalSales / hotProducts
     *   P1-9 新增 —— from / to（趋势区间，回显到表单）、dailyBars（每日销售额，
     *   缺成交的日期补零）、rangeSales / rangeOrders（区间合计）、
     *   categoryShares（分类占比，按销售额倒序）
     */
    @GetMapping
    public String stats(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @RequestParam(required = false) String range,
                        Model model) {
        model.addAttribute("orderCount", orderRepository.count());
        model.addAttribute("paidCount", orderRepository.countByStatusIn(PAID_STATUSES));
        model.addAttribute("totalSales", orderRepository.sumTotalAmountByStatusIn(PAID_STATUSES));
        // PageRequest.of(0, 5) 等价 LIMIT 5 → Top5
        model.addAttribute("hotProducts",
                orderItemRepository.findHotProducts(PAID_STATUSES, PageRequest.of(0, 5)));

        // ---- 趋势区间：from/to 优先，没传则用 range（7/30，默认 7）----
        LocalDate today = LocalDate.now();
        LocalDate start;
        LocalDate end;
        if (from != null && to != null) {
            start = from;
            end = to;
            if (start.isAfter(end)) {           // 用户填反了：整体平移成 end 往前 6 天
                start = end.minusDays(6);
            }
        } else {
            int days = "30".equals(range) ? 30 : 7;
            end = today;
            start = end.minusDays(days - 1L);
        }
        if (end.plusDays(1).isAfter(start.plusDays(MAX_RANGE_DAYS))) {
            start = end.minusDays(MAX_RANGE_DAYS - 1L);   // 超长区间截断，条形图会爆
        }

        List<DailySalesBar> dailyBars = buildDailyBars(start, end);
        BigDecimal rangeSales = dailyBars.stream()
                .map(DailySalesBar::getSales)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long rangeOrders = dailyBars.stream()
                .mapToLong(DailySalesBar::getOrderCount)
                .sum();

        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("dailyBars", dailyBars);
        model.addAttribute("rangeSales", rangeSales);
        model.addAttribute("rangeOrders", rangeOrders);
        model.addAttribute("categoryShares", buildCategoryShares());
        return "admin/stats";
    }

    /**
     * 导出 CSV GET /admin/stats/export.csv（P1-9）。
     *
     * <p>与统计页共用 buildDailyBars——导出的数字和页面看到的完全一致。
     * 文件头写 UTF-8 BOM（\uFEFF）：没有它 Excel 双击打开中文表头必乱码。
     * Content-Type 用 text/csv，Content-Disposition attachment 触发浏览器下载。
     */
    @GetMapping("/export.csv")
    public void exportCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(required = false) String range,
                          HttpServletResponse response) throws IOException {
        LocalDate today = LocalDate.now();
        LocalDate start;
        LocalDate end;
        if (from != null && to != null) {
            start = from;
            end = to;
            if (start.isAfter(end)) {
                start = end.minusDays(6);
            }
        } else {
            int days = "30".equals(range) ? 30 : 7;
            end = today;
            start = end.minusDays(days - 1L);
        }
        List<DailySalesBar> bars = buildDailyBars(start, end);

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"sales_" + start + "_" + end + ".csv\"");
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write('\uFEFF');     // BOM：Excel 才能把 UTF-8 中文表头认对
            writer.println("日期,销售额,订单数");
            for (DailySalesBar bar : bars) {
                writer.println(bar.getDate() + "," + bar.getSales() + "," + bar.getOrderCount());
            }
        }
    }

    /**
     * 区间内逐日销售额（缺成交的日期补零），并算好条形宽度。
     *
     * <p>补零的原因：SQL 的 GROUP BY 只会返回"有成交的日期"，
     * 直接渲染会出现 11-01、11-03、11-07 这种断档，看图的人没法判断
     * "中间没画的日期"是没数据还是漏数据。按日历补齐后图是连续的。
     */
    private List<DailySalesBar> buildDailyBars(LocalDate start, LocalDate end) {
        List<DailySalesStat> rows = orderRepository.sumDailySalesByStatusIn(
                PAID_STATUSES,
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay());
        Map<String, DailySalesStat> byDate = rows.stream()
                .collect(Collectors.toMap(DailySalesStat::getSaleDate, Function.identity(),
                        (a, b) -> a));
        BigDecimal max = rows.stream()
                .map(DailySalesStat::getDailySales)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        List<DailySalesBar> bars = new ArrayList<>();
        for (LocalDate day : start.datesUntil(end.plusDays(1)).toList()) {
            DailySalesStat row = byDate.get(day.toString());
            BigDecimal sales = row != null ? row.getDailySales() : BigDecimal.ZERO;
            bars.add(new DailySalesBar(day, sales,
                    row != null ? row.getOrderCount() : 0L,
                    percentOf(sales, max)));
        }
        return bars;
    }

    /**
     * 各分类销售占比（按销售额倒序），含百分比与条形宽度。
     */
    private List<CategoryShare> buildCategoryShares() {
        List<CategorySalesStat> rows = orderItemRepository.findCategorySales(PAID_STATUSES);
        if (rows.isEmpty()) {
            return List.of();
        }
        // 分类名一次查全（分类表最多十几行，没必要按 id 逐条查）
        Map<Long, String> names = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        BigDecimal catTotal = rows.stream()
                .map(CategorySalesStat::getSales)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal catMax = rows.stream()
                .map(CategorySalesStat::getSales)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        return rows.stream()
                .sorted(Comparator.comparing(CategorySalesStat::getSales).reversed())
                .map(r -> {
                    String name = names.getOrDefault(r.getCategoryId(), "分类#" + r.getCategoryId());
                    double percent = catTotal.signum() == 0 ? 0.0
                            : r.getSales().multiply(HUNDRED)
                                .divide(catTotal, 1, RoundingMode.HALF_UP).doubleValue();
                    return new CategoryShare(name, r.getSales(), percent,
                            percentOf(r.getSales(), catMax), r.getTotalQty());
                })
                .toList();
    }

    /** sales / max × 100 取整（max 为 0 时返回 0，避免除零）。 */
    private int percentOf(BigDecimal sales, BigDecimal max) {
        if (max.signum() == 0) {
            return 0;
        }
        return sales.multiply(HUNDRED).divide(max, 0, RoundingMode.HALF_UP).intValue();
    }
}
