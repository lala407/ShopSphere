// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AdminOrderPaginationTest —— 后台订单分页 + 多条件筛选回归测试（P1-6）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.common.OrderStatus;
import com.shopsphere.common.UserRole;
import com.shopsphere.entity.Order;
import com.shopsphere.entity.User;
import com.shopsphere.repository.CartItemRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后台订单列表分页 + 筛选集成测试（H2 内存库）。
 *
 * <p>对应缺陷清单 P1-6：修复前 {@code AdminOrderController.list} 用 findAll 全量加载，
 * 订单上几百条就卡，且只有状态一个筛选条件。
 * 修复后：每页 10 条 + 状态/关键字/日期区间三组条件，共 2^4 = 16 种组合。
 *
 * <p>数据构造（13 单，便于观察 10 + 3 的两页切分）：
 * <ul>
 *   <li>第 1~8 单 CREATED，第 9~13 单 PAID（状态筛选有 5 条可查）；</li>
 *   <li>收货人依次为「收货人01」…「收货人13」，订单号 ORDTEST0001…0013（关键字筛选）；</li>
 *   <li>下单时间 2026-11-01 起每天一单（日期区间筛选，也能验证"截止日当天算在内"）。</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminOrderPaginationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    private static final int TOTAL_ORDERS = 13;
    private static final int PAGE_SIZE = 10;
    /** 下单时间起点：2026-11-01 10:00，第 i 单 = 起点 + (i-1) 天。 */
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 1, 10, 0);

    @BeforeEach
    void setUp() {
        // 外键顺序：明细 → 订单 → 购物车 → 商品 → 分类 → 用户
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // 下单人（订单的 user_id 非空，必须有真实用户才能落库）
        User buyer = new User();
        buyer.setUsername("pg_buyer");
        buyer.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        buyer.setEmail("pg_buyer@example.com");
        buyer.setRole(UserRole.CUSTOMER);
        buyer.setStatus(1);
        buyer.setCreatedAt(BASE_TIME);
        userRepository.save(buyer);

        for (int i = 1; i <= TOTAL_ORDERS; i++) {
            Order o = new Order();
            o.setOrderNo(String.format("ORDTEST%04d", i));
            o.setUser(buyer);
            o.setTotalAmount(new BigDecimal("100.00").add(new BigDecimal(i)));
            // 前 8 单待付款，后 5 单已付款
            o.setStatus(i <= 8 ? OrderStatus.CREATED : OrderStatus.PAID);
            o.setReceiverName(String.format("收货人%02d", i));
            o.setReceiverPhone("13800138000");
            o.setReceiverAddress("广州市天河区测试路 " + i + " 号");
            o.setCreatedAt(BASE_TIME.plusDays(i - 1));
            o.setUpdatedAt(BASE_TIME.plusDays(i - 1));
            orderRepository.save(o);
        }
    }

    private MockHttpSession adminSession() {
        MockHttpSession admin = new MockHttpSession();
        admin.setAttribute("userId", 999L);
        admin.setAttribute("role", UserRole.ADMIN);
        return admin;
    }

    /** 发一次 GET /admin/order 请求，取回渲染后的 Page 模型。 */
    @SuppressWarnings("unchecked")
    private Page<Order> fetchPage(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).session(adminSession()))
                .andExpect(status().isOk())
                .andReturn();
        return (Page<Order>) result.getModelAndView().getModel().get("pageData");
    }

    @Test
    @DisplayName("分页切分：13 单 → 首页 10 条 + 第 2 页 3 条，按下单时间倒序")
    void paginationSplitsInto10And3() throws Exception {
        Page<Order> first = fetchPage("/admin/order?page=0");
        assertEquals(TOTAL_ORDERS, first.getTotalElements(), "总条数应为 13");
        assertEquals(2, first.getTotalPages(), "13 条 / 每页 10 条 = 2 页");
        assertEquals(PAGE_SIZE, first.getContent().size(), "首页应满 10 条");
        assertTrue(first.isFirst() && !first.isLast());
        // 倒序：最新的第 13 单排第一
        assertEquals("ORDTEST0013", first.getContent().get(0).getOrderNo());

        Page<Order> second = fetchPage("/admin/order?page=1");
        assertEquals(3, second.getContent().size(), "第 2 页应剩 3 条");
        assertTrue(second.isLast());
        assertEquals("ORDTEST0003", second.getContent().get(0).getOrderNo(), "第 2 页从第 3 单开始");
    }

    @Test
    @DisplayName("按状态筛选：PAID 只剩 5 单（待付款的 8 单被过滤）")
    void filterByStatus() throws Exception {
        Page<Order> paid = fetchPage("/admin/order?status=PAID");
        assertEquals(5, paid.getTotalElements(), "后 5 单为已付款");
        assertTrue(paid.getContent().stream().allMatch(o -> o.getStatus() == OrderStatus.PAID));

        Page<Order> created = fetchPage("/admin/order?status=CREATED");
        assertEquals(8, created.getTotalElements(), "前 8 单为待付款");
    }

    @Test
    @DisplayName("关键字筛选：订单号 / 收货人 / 用户名三个字段都能命中，空白串视为不过滤")
    void filterByKeyword() throws Exception {
        assertEquals(1, fetchPage("/admin/order?kw=ORDTEST0012").getTotalElements(), "订单号精确命中");
        assertEquals(1, fetchPage("/admin/order?kw=收货人12").getTotalElements(), "收货人命中");
        assertEquals(TOTAL_ORDERS, fetchPage("/admin/order?kw=pg_buyer").getTotalElements(), "用户名命中全部");
        // 表单没填时提交的是 kw=（空串），必须当"不过滤"处理，否则页面会变成空列表
        assertEquals(TOTAL_ORDERS, fetchPage("/admin/order?kw=").getTotalElements(), "空关键字视为不过滤");
        assertEquals(0, fetchPage("/admin/order?kw=不存在的单号").getTotalElements());
    }

    @Test
    @DisplayName("日期区间筛选：左闭右开，截止日当天的订单也算在内")
    void filterByDateRange() throws Exception {
        // 2026-11-05 10:00（第 5 单）~ 2026-11-07 10:00（第 7 单）
        Page<Order> range = fetchPage("/admin/order?from=2026-11-05&to=2026-11-07");
        assertEquals(3, range.getTotalElements(), "应命中第 5、6、7 三单");
        assertTrue(range.getContent().stream()
                        .allMatch(o -> o.getOrderNo().matches("ORDTEST000[567]")),
                "区间外的订单不应出现");

        // 只给 from：从第 11 单起（11-11 ~ 11-13）共 3 单
        assertEquals(3, fetchPage("/admin/order?from=2026-11-11").getTotalElements());
        // 只给 to：到 11-02 为止共 2 单
        assertEquals(2, fetchPage("/admin/order?to=2026-11-02").getTotalElements());
    }

    @Test
    @DisplayName("翻页链接保留筛选条件，否则一翻页筛选就丢了")
    void paginationLinksKeepFilters() throws Exception {
        // 用 11 单已付款 + 2 单待付款的另一组数据不好造，这里直接验证链接里带参数
        String html = mockMvc.perform(get("/admin/order?kw=ORDTEST&page=0").session(adminSession()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("kw=ORDTEST"), "分页链接应带上关键字参数");
        assertTrue(html.contains("page=1"), "应渲染出第 2 页链接");
    }
}
