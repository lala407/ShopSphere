// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：OrderLifecycleTest —— 订单状态机闭环回归测试（P1-3）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.common.OrderStatus;
import com.shopsphere.common.ProductStatus;
import com.shopsphere.common.UserRole;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Order;
import com.shopsphere.entity.Product;
import com.shopsphere.repository.CartItemRepository;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.OrderItemRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.ProductRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单状态机闭环集成测试（H2 内存库）。
 *
 * <p>对应缺陷清单 P1-3：修复前 COMPLETED 没有任何流转入口（ship 只到 SHIPPED）、
 * 用户不能取消自己的待付款订单。修复后全链路可演示：
 * <pre>
 * CREATED ─付款─> PAID ─发货─> SHIPPED ─确认收货─> COMPLETED
 *    │
 *    └─用户取消（仅 CREATED）─> CANCELLED（回补库存）
 * </pre>
 * <ol>
 *   <li>用户取消待付款订单：CREATED → CANCELLED，库存回补、销量不动；</li>
 *   <li>用户不能取消已付款订单（该路径收口到后台管理员）；</li>
 *   <li>确认收货：SHIPPED → COMPLETED，completed_at 落值（COMPLETED 唯一入口）；</li>
 *   <li>状态守卫：非 SHIPPED 不能确认收货（CREATED 直接 confirm 被拒）。</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    private static final int SEED_SALES = 50;
    private static final int SEED_STOCK = 10;

    @BeforeEach
    void setUp() {
        // 外键顺序：明细 → 订单 → 购物车 → 商品 → 分类
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = new Category();
        category.setName("状态机测试分类");
        category.setSortOrder(1);
        categoryRepository.save(category);

        Product p = new Product();
        p.setName("状态机测试商品");
        p.setDescription("P1-3 测试商品");
        p.setPrice(new BigDecimal("20.00"));
        p.setStock(SEED_STOCK);
        p.setCategoryId(category.getId());
        p.setImageUrl("");
        p.setStatus(ProductStatus.ON_SALE);
        p.setSales(SEED_SALES);
        productRepository.save(p);
    }

    private MockHttpSession loginAsUser(String name) throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", name)
                        .param("password", "123456")
                        .param("email", name + "@example.com"))
                .andExpect(status().is3xxRedirection());
        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", name)
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private MockHttpSession adminSession() {
        MockHttpSession admin = new MockHttpSession();
        admin.setAttribute("userId", 999L);
        admin.setAttribute("role", UserRole.ADMIN);
        return admin;
    }

    /** 当前用户唯一一张订单（每个用例只下一单）。 */
    private Order onlyOrder() {
        return orderRepository.findAll().get(0);
    }

    private void createOrder(MockHttpSession session, int qty) throws Exception {
        mockMvc.perform(post("/cart/add")
                        .param("productId", productRepository.findAll().get(0).getId().toString())
                        .param("quantity", String.valueOf(qty))
                        .session(session))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/order/create")
                        .param("receiverName", "测试收货人")
                        .param("receiverPhone", "13500135000")
                        .param("receiverAddress", "南京市鼓楼区测试路 9 号")
                        .session(session))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("用户取消待付款订单：CREATED → CANCELLED，库存回补、销量不动")
    void userCancelsCreatedOrder() throws Exception {
        MockHttpSession session = loginAsUser("lc_user1");
        createOrder(session, 3);

        Order created = onlyOrder();
        assertEquals(OrderStatus.CREATED, created.getStatus());

        mockMvc.perform(post("/orders/" + created.getId() + "/cancel").session(session))
                .andExpect(status().is3xxRedirection());

        Order cancelled = orderRepository.findById(created.getId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertNotNull(cancelled.getCancelledAt(), "取消时间应落值");
        assertEquals(SEED_STOCK, productRepository.findAll().get(0).getStock(), "库存应回补");
        assertEquals(SEED_SALES, productRepository.findAll().get(0).getSales(), "待付款订单从未计销量");
    }

    @Test
    @DisplayName("用户不能取消已付款订单（该路径收口到后台），状态保持 PAID")
    void userCannotCancelPaidOrder() throws Exception {
        MockHttpSession session = loginAsUser("lc_user2");
        createOrder(session, 1);
        Long orderId = onlyOrder().getId();
        mockMvc.perform(post("/orders/" + orderId + "/pay").session(session))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/orders/" + orderId + "/cancel").session(session))
                .andExpect(status().is3xxRedirection());

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.PAID, order.getStatus(), "已付款订单不允许用户自行取消");
        assertNull(order.getCancelledAt());
    }

    @Test
    @DisplayName("全链路：下单→付款→发货→用户确认收货，COMPLETED 落 completed_at")
    void fullLifecycleReachesCompleted() throws Exception {
        MockHttpSession session = loginAsUser("lc_user3");
        createOrder(session, 2);
        Long orderId = onlyOrder().getId();

        mockMvc.perform(post("/orders/" + orderId + "/pay").session(session))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/order/" + orderId + "/ship").session(adminSession()))
                .andExpect(status().is3xxRedirection());
        assertEquals(OrderStatus.SHIPPED, orderRepository.findById(orderId).orElseThrow().getStatus());

        mockMvc.perform(post("/orders/" + orderId + "/confirm").session(session))
                .andExpect(status().is3xxRedirection());

        Order completed = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, completed.getStatus(), "确认收货后应到 COMPLETED");
        assertNotNull(completed.getCompletedAt(), "完成时间应落值");
        // COMPLETED 是终态：销量不因收货再变（付款时已累加 2）
        assertEquals(SEED_SALES + 2, productRepository.findAll().get(0).getSales());
    }

    @Test
    @DisplayName("状态守卫：待付款订单直接确认收货被拒，状态不变")
    void confirmRejectedForCreatedOrder() throws Exception {
        MockHttpSession session = loginAsUser("lc_user4");
        createOrder(session, 1);
        Long orderId = onlyOrder().getId();

        mockMvc.perform(post("/orders/" + orderId + "/confirm").session(session))
                .andExpect(status().is3xxRedirection());

        assertEquals(OrderStatus.CREATED, orderRepository.findById(orderId).orElseThrow().getStatus(),
                "非 SHIPPED 订单不允许确认收货");
    }
}
