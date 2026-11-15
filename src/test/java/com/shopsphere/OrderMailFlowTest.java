// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：OrderMailFlowTest —— 下单→付款→发货 邮件链路集成测试（Day8）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.common.MailStatus;
import com.shopsphere.common.MailType;
import com.shopsphere.common.OrderStatus;
import com.shopsphere.common.ProductStatus;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.MailLog;
import com.shopsphere.entity.Order;
import com.shopsphere.entity.Product;
import com.shopsphere.repository.CartItemRepository;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.MailLogRepository;
import com.shopsphere.repository.OrderItemRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import com.shopsphere.service.OrderService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

/**
 * 邮件链路集成测试（跑在 H2 内存库上，不依赖本机 MySQL，也不发真实邮件）。
 *
 * <p>【这个测试到底在验证什么】
 * <ol>
 *   <li>注册 → 登录 → 加购 → 下单 → 付款 → 发货 整条链路能走通；</li>
 *   <li>每一步都往 mail_log 写了一条记录（这是 ex.html「邮件确认」需求的落地证据）；</li>
 *   <li>没配 SMTP 账号时状态是 SKIPPED，**订单照常创建**——证明发信失败不会拖垮主流程；</li>
 *   <li>订单详情页能正常渲染（Thymeleaf 表达式没写错），并显示"邮件通知"区块。</li>
 * </ol>
 *
 * <p>【为什么测试类不能加 @Transactional】
 * 加了之后整个测试方法跑在一个事务里，方法结束才回滚、永不提交；
 * 而邮件监听器是 AFTER_COMMIT 才触发的 —— 事务不提交，监听器就永远不执行，
 * 测试会"假通过"（日志一条都没有）。这是 AFTER_COMMIT 方案最容易踩的坑。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderMailFlowTest {

    @Autowired
    private MockMvc mockMvc;

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

    @Autowired
    private OrderService orderService;

    private Long productId;

    @BeforeEach
    void setUp() {
        // 外键顺序：明细 → 订单 → 购物车 → 商品 → 分类 → 用户
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
        product.setName("测试商品");
        product.setDescription("仅用于测试");
        product.setPrice(new BigDecimal("99.00"));
        product.setStock(10);
        product.setCategoryId(category.getId());
        product.setImageUrl("");
        product.setStatus(ProductStatus.ON_SALE);
        productRepository.save(product);

        productId = product.getId();
    }

    @Test
    @DisplayName("下单/付款/发货各写一条邮件日志，且订单详情页可渲染")
    void orderFlowWritesMailLog() throws Exception {

        // ---------- 1. 注册（邮箱必填） ----------
        mockMvc.perform(post("/register")
                        .param("username", "tom")
                        .param("password", "123456")
                        .param("email", "tom@example.com"))
                .andExpect(status().is3xxRedirection());

        // ---------- 2. 登录，拿 Session ----------
        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", "tom")
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session, "登录成功后 Session 里应该有 userId");

        // ---------- 3. 加入购物车 ----------
        mockMvc.perform(post("/cart/add")
                        .param("productId", String.valueOf(productId))
                        .param("quantity", "2")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        // ---------- 4. 提交订单 ----------
        mockMvc.perform(post("/order/create")
                        .param("receiverName", "张三")
                        .param("receiverPhone", "13800138000")
                        .param("receiverAddress", "北京市海淀区测试路 1 号")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        Order order = orderRepository.findAll().get(0);
        assertEquals(OrderStatus.CREATED, order.getStatus());
        // 下单扣库存：10 - 2 = 8
        assertEquals(8, productRepository.findById(productId).orElseThrow().getStock());

        // ---------- 5. 下单邮件日志：状态 SKIPPED（测试环境没配 SMTP），但记录必须在 ----------
        List<MailLog> logs = mailLogRepository.findByOrderIdOrderByIdAsc(order.getId());
        assertEquals(1, logs.size(), "下单成功后应该有一条邮件日志");
        assertEquals(MailType.ORDER_CREATED, logs.get(0).getType());
        assertEquals(MailStatus.SKIPPED, logs.get(0).getStatus());
        assertEquals("tom@example.com", logs.get(0).getToEmail());

        // ---------- 6. 订单详情页渲染 + 展示"邮件通知"区块 ----------
        mockMvc.perform(get("/orders/" + order.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("邮件通知")))
                .andExpect(content().string(containsString("未发送")));

        // ---------- 7. 付款 → 第二封（付款成功） ----------
        mockMvc.perform(post("/orders/" + order.getId() + "/pay").session(session))
                .andExpect(status().is3xxRedirection());

        logs = mailLogRepository.findByOrderIdOrderByIdAsc(order.getId());
        assertEquals(2, logs.size());
        assertEquals(MailType.ORDER_PAID, logs.get(1).getType());

        // ---------- 8. 后台发货 → 第三封（发货通知，ex.html 要求的"确认发货"邮件） ----------
        orderService.ship(order.getId());

        logs = mailLogRepository.findByOrderIdOrderByIdAsc(order.getId());
        assertEquals(3, logs.size());
        assertEquals(MailType.ORDER_SHIPPED, logs.get(2).getType());
        assertEquals(OrderStatus.SHIPPED,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("注册不填邮箱：留在注册页并给出中文提示")
    void registerWithoutEmailShowsFriendlyError() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "jerry")
                        .param("password", "123456"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", containsString("邮箱")));

        assertEquals(0, userRepository.count(), "校验失败不应该落库");
    }
}
