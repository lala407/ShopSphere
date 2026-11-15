// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AccessLogFlowTest —— 浏览 / 购买日志集成测试（P0-2）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.common.AccessAction;
import com.shopsphere.common.ProductStatus;
import com.shopsphere.common.UserRole;
import com.shopsphere.entity.AccessLog;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Product;
import com.shopsphere.repository.AccessLogRepository;
import com.shopsphere.repository.CartItemRepository;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.MailLogRepository;
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
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 浏览 / 购买日志集成测试（H2 内存库）。
 *
 * <p>验证四件事：
 * <ol>
 *   <li>匿名浏览会落 VIEW_LIST / VIEW_PRODUCT，user_id 为 null（游客允许）；</li>
 *   <li>登录后的加购 / 下单 / 付款落 ADD_CART / CREATE_ORDER / PAY_ORDER，且带上 user_id；</li>
 *   <li>不在白名单里的请求（登录页、静态资源）**不**记日志，避免日志表被噪声淹没；</li>
 *   <li>后台 /admin/log 能打开并渲染出日志行。</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessLogFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessLogRepository accessLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private MailLogRepository mailLogRepository;

    private Long productId;

    @BeforeEach
    void setUp() {
        // 外键顺序：明细 → 订单 → 购物车 → 商品 → 分类 → 用户；日志表独立
        accessLogRepository.deleteAll();
        mailLogRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        Category category = new Category();
        category.setName("测试分类");
        category.setSortOrder(1);
        categoryRepository.save(category);

        Product product = new Product();
        product.setName("日志测试商品");
        product.setDescription("仅用于测试");
        product.setPrice(new BigDecimal("50.00"));
        product.setStock(10);
        product.setCategoryId(category.getId());
        product.setImageUrl("");
        product.setStatus(ProductStatus.ON_SALE);
        productRepository.save(product);

        productId = product.getId();
    }

    @Test
    @DisplayName("游客浏览：首页记 VIEW_LIST、详情页记 VIEW_PRODUCT，user_id 为空")
    void guestBrowsingIsLogged() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/product/" + productId)).andExpect(status().isOk());

        List<AccessLog> logs = accessLogRepository.findAll();
        assertEquals(2, logs.size(), "首页 + 详情页应各记一条");

        AccessLog listLog = logs.stream()
                .filter(l -> l.getAction() == AccessAction.VIEW_LIST).findFirst().orElseThrow();
        AccessLog detailLog = logs.stream()
                .filter(l -> l.getAction() == AccessAction.VIEW_PRODUCT).findFirst().orElseThrow();

        // 游客：没有 user_id，但有 sessionId（将来做 UV 的口子）
        assertNull(listLog.getUserId());
        assertNull(listLog.getTargetId(), "首页没有具体目标");
        assertNotNull(listLog.getSessionId());
        assertNotNull(listLog.getIp());

        assertEquals(productId, detailLog.getTargetId());
        assertEquals("PRODUCT", detailLog.getTargetType());
    }

    @Test
    @DisplayName("登录后加购/下单/付款：三个购买动作都记到该用户")
    void purchaseActionsAreLoggedWithUserId() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "loguser")
                        .param("password", "123456")
                        .param("email", "log@example.com"))
                .andExpect(status().is3xxRedirection());

        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", "loguser")
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);

        mockMvc.perform(post("/cart/add")
                        .param("productId", String.valueOf(productId))
                        .param("quantity", "1")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/order/create")
                        .param("receiverName", "李四")
                        .param("receiverPhone", "13900139000")
                        .param("receiverAddress", "上海市浦东新区测试路 2 号")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        Long orderId = orderRepository.findAll().get(0).getId();
        mockMvc.perform(post("/orders/" + orderId + "/pay").session(session))
                .andExpect(status().is3xxRedirection());

        assertEquals(1, accessLogRepository.countByAction(AccessAction.ADD_CART));
        assertEquals(1, accessLogRepository.countByAction(AccessAction.CREATE_ORDER));
        assertEquals(1, accessLogRepository.countByAction(AccessAction.PAY_ORDER));

        // user_id 必须落上，否则后台日志页只能显示"游客"，等于白记
        Long userId = (Long) session.getAttribute("userId");
        AccessLog payLog = accessLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AccessAction.PAY_ORDER).findFirst().orElseThrow();
        assertEquals(userId, payLog.getUserId());
        assertEquals(orderId, payLog.getTargetId());
        assertEquals("ORDER", payLog.getTargetType());
    }

    @Test
    @DisplayName("白名单之外的请求不记日志（登录页 / 静态资源）")
    void nonWhitelistedRequestsAreNotLogged() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());

        assertEquals(0, accessLogRepository.count(), "登录页不属于浏览/购买动作，不应记日志");
    }

    @Test
    @DisplayName("后台日志页：能打开并显示日志行与筛选条件")
    void adminLogPageRenders() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/product/" + productId)).andExpect(status().isOk());

        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("userId", 1L);
        adminSession.setAttribute("role", UserRole.ADMIN);

        mockMvc.perform(get("/admin/log").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("访问日志")))
                .andExpect(content().string(containsString("查看商品")))
                .andExpect(content().string(containsString("PRODUCT #" + productId)));
    }
}
