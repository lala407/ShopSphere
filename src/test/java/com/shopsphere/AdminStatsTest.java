// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AdminStatsTest —— 统计报表 P1-9 增强（趋势/占比/筛选/CSV）回归测试
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.common.OrderStatus;
import com.shopsphere.common.ProductStatus;
import com.shopsphere.common.UserRole;
import com.shopsphere.controller.AdminStatsController;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Order;
import com.shopsphere.entity.OrderItem;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.OrderItemRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统计报表集成测试（H2 内存库，P1-9 增强）。
 *
 * <p>修复前统计页只有 4 个数字 + Top5，没有时间维度和分类维度。
 * 修复后：① 每日销售额条形图（按付款时间，缺成交日补零）② 分类销售占比
 * ③ 日期范围筛选（from/to + 近7/30天快捷）④ 导出 CSV（与页面同一数据源）。
 *
 * <p>数据构造（重点验证「口径」）：
 * <ul>
 *   <li>订单 1：PAID，paidAt = 今天，¥100 —— 计入今日销售额与「电子产品」分类；</li>
 *   <li>订单 2：PAID，paidAt = 昨天，¥50 —— 计入昨日销售额与「家居日用」分类；</li>
 *   <li>订单 3：CREATED（未付款），createdAt = 今天，¥999 —— 指标卡「订单总数」+1，
 *       但趋势、占比、区间销售额都不能算它（与总销售额同口径）。</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminStatsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    private Long techCategoryId;
    private Long homeCategoryId;
    private String todayStr;
    private String yesterdayStr;

    @BeforeEach
    void setUp() {
        // 外键顺序：明细 → 订单 → 购物车 → 商品 → 分类 → 用户
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        todayStr = today.toString();
        yesterdayStr = yesterday.toString();

        User buyer = new User();
        buyer.setUsername("stats_buyer");
        buyer.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        buyer.setEmail("stats_buyer@example.com");
        buyer.setRole(UserRole.CUSTOMER);
        buyer.setStatus(1);
        buyer.setCreatedAt(yesterday.atTime(9, 0));
        buyer = userRepository.save(buyer);

        Category tech = new Category();
        tech.setName("电子产品");
        tech.setSortOrder(1);
        tech = categoryRepository.save(tech);
        techCategoryId = tech.getId();

        Category home = new Category();
        home.setName("家居日用");
        home.setSortOrder(2);
        home = categoryRepository.save(home);
        homeCategoryId = home.getId();

        Product headphone = new Product();
        headphone.setName("蓝牙耳机");
        headphone.setPrice(new BigDecimal("100.00"));
        headphone.setStock(10);
        headphone.setCategoryId(techCategoryId);
        headphone.setStatus(ProductStatus.ON_SALE);
        headphone = productRepository.save(headphone);

        Product mug = new Product();
        mug.setName("陶瓷马克杯");
        mug.setPrice(new BigDecimal("50.00"));
        mug.setStock(20);
        mug.setCategoryId(homeCategoryId);
        mug.setStatus(ProductStatus.ON_SALE);
        mug = productRepository.save(mug);

        // 订单 1：今天付款 ¥100（电子产品）
        Order paidToday = baseOrder("ORDSTAT0001", buyer, "100.00", today.atTime(10, 0));
        paidToday.setStatus(OrderStatus.PAID);
        paidToday.setPaidAt(today.atTime(10, 5));
        paidToday = orderRepository.save(paidToday);
        saveItem(paidToday, headphone.getId(), "蓝牙耳机", "100.00", 1);

        // 订单 2：昨天付款 ¥50（家居日用）
        Order paidYesterday = baseOrder("ORDSTAT0002", buyer, "50.00", yesterday.atTime(10, 0));
        paidYesterday.setStatus(OrderStatus.PAID);
        paidYesterday.setPaidAt(yesterday.atTime(10, 5));
        paidYesterday = orderRepository.save(paidYesterday);
        saveItem(paidYesterday, mug.getId(), "陶瓷马克杯", "50.00", 1);

        // 订单 3：待付款 ¥999（只进「订单总数」，不进销售额口径）
        Order unpaid = baseOrder("ORDSTAT0003", buyer, "999.00", today.atTime(11, 0));
        unpaid.setStatus(OrderStatus.CREATED);
        orderRepository.save(unpaid);
    }

    private Order baseOrder(String orderNo, User user, String total, LocalDateTime createdAt) {
        Order o = new Order();
        o.setOrderNo(orderNo);
        o.setUser(user);
        o.setTotalAmount(new BigDecimal(total));
        o.setReceiverName("收货人");
        o.setReceiverPhone("13800138000");
        o.setReceiverAddress("广州市天河区统计测试路 1 号");
        o.setCreatedAt(createdAt);
        o.setUpdatedAt(createdAt);
        return o;
    }

    private void saveItem(Order order, Long productId, String productName, String price, int qty) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductId(productId);
        item.setProductName(productName);
        item.setProductPrice(new BigDecimal(price));
        item.setQuantity(qty);
        item.setSubtotal(new BigDecimal(price).multiply(BigDecimal.valueOf(qty)));
        orderItemRepository.save(item);
    }

    private MockHttpSession adminSession() {
        MockHttpSession admin = new MockHttpSession();
        admin.setAttribute("userId", 999L);
        admin.setAttribute("role", UserRole.ADMIN);
        return admin;
    }

    @SuppressWarnings("unchecked")
    private List<AdminStatsController.DailySalesBar> fetchBars(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).session(adminSession()))
                .andExpect(status().isOk())
                .andReturn();
        return (List<AdminStatsController.DailySalesBar>)
                result.getModelAndView().getModel().get("dailyBars");
    }

    @Test
    @DisplayName("每日趋势：默认近 7 天，按付款日聚合且缺成交日补零，待付款不进口径")
    void dailyBarsFillMissingDaysAndExcludeUnpaid() throws Exception {
        List<AdminStatsController.DailySalesBar> bars = fetchBars("/admin/stats");

        // 近 7 天 = 今天往前 6 天，共 7 行，首行是 6 天前
        assertEquals(7, bars.size(), "默认区间应渲染 7 天（一天一行）");
        assertEquals(LocalDate.now().minusDays(6), bars.get(0).getDate());
        assertEquals(LocalDate.now(), bars.get(6).getDate());

        AdminStatsController.DailySalesBar todayBar = bars.get(6);
        AdminStatsController.DailySalesBar yesterdayBar = bars.get(5);
        assertEquals(0, todayBar.getSales().compareTo(new BigDecimal("100")),
                "今天付款 ¥100（待付款 ¥999 不计入）");
        assertEquals(1L, todayBar.getOrderCount());
        assertEquals(0, yesterdayBar.getSales().compareTo(new BigDecimal("50")),
                "昨天付款 ¥50");
        assertEquals(0L, bars.get(0).getOrderCount(), "6 天前无成交应补零");
        assertEquals(0, bars.get(0).getSales().compareTo(BigDecimal.ZERO));
        // 条形宽度：今天 100 是区间最大值 → 100%；有成交但不是最大的日 → 50%
        assertEquals(100, todayBar.getWidthPercent());
        assertEquals(50, yesterdayBar.getWidthPercent());
    }

    @Test
    @DisplayName("日期筛选：from/to 收窄区间，区间合计只含已付款")
    void dateRangeFilter() throws Exception {
        List<AdminStatsController.DailySalesBar> bars =
                fetchBars("/admin/stats?from=" + yesterdayStr + "&to=" + yesterdayStr);
        assertEquals(1, bars.size(), "单日区间应只有一行");
        assertEquals(0, bars.get(0).getSales().compareTo(new BigDecimal("50")),
                "只统计昨天付款的 ¥50");

        MvcResult statsPage = mockMvc.perform(get("/admin/stats?from=" + yesterdayStr + "&to=" + yesterdayStr)
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(0, ((BigDecimal) statsPage.getModelAndView().getModel().get("rangeSales"))
                        .compareTo(new BigDecimal("50")),
                "区间合计应只含已付款 ¥50");
        assertEquals(1L, statsPage.getModelAndView().getModel().get("rangeOrders"));
    }

    @Test
    @DisplayName("分类占比：按销售额倒序 + 百分比，待付款订单不计入")
    void categoryShares() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/stats").session(adminSession()))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        List<AdminStatsController.CategoryShare> shares = (List<AdminStatsController.CategoryShare>)
                result.getModelAndView().getModel().get("categoryShares");

        assertEquals(2, shares.size(), "两个分类都有成交");
        assertEquals("电子产品", shares.get(0).getName(), "¥100 应排第一");
        assertEquals(0, shares.get(0).getSales().compareTo(new BigDecimal("100")));
        assertEquals("家居日用", shares.get(1).getName());
        assertEquals(0, shares.get(1).getSales().compareTo(new BigDecimal("50")));
        // 占比 = 100/150、50/150（保留 1 位小数）
        assertEquals(66.7, shares.get(0).getPercent(), 0.01);
        assertEquals(33.3, shares.get(1).getPercent(), 0.01);
    }

    @Test
    @DisplayName("CSV 导出：BOM + 表头 + 每日一行，数字与页面一致")
    void exportCsv() throws Exception {
        MvcResult result = mockMvc
                .perform(get("/admin/stats/export.csv?from=" + yesterdayStr + "&to=" + yesterdayStr)
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.startsWith("\uFEFF"), "必须以 UTF-8 BOM 开头（Excel 兼容）");
        assertTrue(body.contains("日期,销售额,订单数"), "应有中文表头");
        assertTrue(body.contains(yesterdayStr + ",50.00,1"), "昨日一行：¥50 / 1 单");
        assertTrue(!body.contains(todayStr), "区间外（今天）不应出现在导出里");
        assertTrue(!body.contains("999"), "待付款 ¥999 无论如何不进销售额");
        assertTrue(result.getResponse().getContentType().contains("text/csv"));
    }
}
