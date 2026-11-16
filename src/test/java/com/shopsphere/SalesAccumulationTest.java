// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：SalesAccumulationTest —— 销量随付款累加回归测试（P1-2）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.common.ProductStatus;
import com.shopsphere.common.UserRole;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Product;
import com.shopsphere.repository.CartItemRepository;
import com.shopsphere.repository.CategoryRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 销量累加集成测试（H2 内存库）。
 *
 * <p>对应缺陷清单 P1-2：修复前 sales 只有 data.sql 写死的种子数，
 * 下单/付款都不累加，前台「已售 N 件」是假数据。修复后的口径：
 * <ol>
 *   <li>仅下单（CREATED）不累加——待付款订单可能取消，不能提前计销量；</li>
 *   <li>付款成功（PAID）累加 quantity，与统计页热销 Top5（按已付款聚合）同口径；</li>
 *   <li>已付款订单取消 → 库存回补 + 销量对称回退；</li>
 *   <li>待付款订单取消 → 库存回补，销量本来就没加过，保持不变。</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesAccumulationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private Long productId;
    private static final int SEED_SALES = 100;
    private static final int SEED_STOCK = 10;

    @BeforeEach
    void setUp() {
        cartItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = new Category();
        category.setName("销量测试分类");
        category.setSortOrder(1);
        categoryRepository.save(category);

        Product p = new Product();
        p.setName("销量测试商品");
        p.setDescription("P1-2 测试商品");
        p.setPrice(new BigDecimal("30.00"));
        p.setStock(SEED_STOCK);
        p.setCategoryId(category.getId());
        p.setImageUrl("");
        p.setStatus(ProductStatus.ON_SALE);
        p.setSales(SEED_SALES);
        productId = productRepository.save(p).getId();
    }

    /** 注册 + 登录一个用户，返回会话。 */
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

    @Test
    @DisplayName("下单不累加、付款累加：付款后 sales = 种子 + 购买量，库存同步扣减")
    void salesIncreasesOnlyAfterPayment() throws Exception {
        MockHttpSession session = loginAsUser("salesuser1");

        mockMvc.perform(post("/cart/add")
                        .param("productId", String.valueOf(productId))
                        .param("quantity", "3")
                        .session(session))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/order/create")
                        .param("receiverName", "王五")
                        .param("receiverPhone", "13800138000")
                        .param("receiverAddress", "广州市天河区测试路 1 号")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        // 仅下单：库存已扣（下单即扣），但销量必须还是种子值
        Product afterCreate = productRepository.findById(productId).orElseThrow();
        assertEquals(SEED_SALES, afterCreate.getSales(), "待付款订单不应计销量");
        assertEquals(SEED_STOCK - 3, afterCreate.getStock(), "库存应在下单时扣减");

        // 付款：销量累加 3
        mockMvc.perform(post("/orders/" + latestOrderId(session) + "/pay").session(session))
                .andExpect(status().is3xxRedirection());

        Product afterPay = productRepository.findById(productId).orElseThrow();
        assertEquals(SEED_SALES + 3, afterPay.getSales(), "付款后销量应累加购买量");
    }

    @Test
    @DisplayName("已付款订单取消：库存回补 + 销量对称回退")
    void paidOrderCancelRestoresSalesAndStock() throws Exception {
        MockHttpSession session = loginAsUser("salesuser2");

        mockMvc.perform(post("/cart/add")
                        .param("productId", String.valueOf(productId))
                        .param("quantity", "2")
                        .session(session))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/order/create")
                        .param("receiverName", "赵六")
                        .param("receiverPhone", "13700137000")
                        .param("receiverAddress", "深圳市南山区测试路 3 号")
                        .session(session))
                .andExpect(status().is3xxRedirection());
        Long orderId = latestOrderId(session);
        mockMvc.perform(post("/orders/" + orderId + "/pay").session(session))
                .andExpect(status().is3xxRedirection());

        // 管理员取消
        MockHttpSession admin = new MockHttpSession();
        admin.setAttribute("userId", 999L);
        admin.setAttribute("role", UserRole.ADMIN);
        mockMvc.perform(post("/admin/order/" + orderId + "/cancel").session(admin))
                .andExpect(status().is3xxRedirection());

        Product p = productRepository.findById(productId).orElseThrow();
        assertEquals(SEED_SALES, p.getSales(), "已付款订单取消后销量应回退到种子值");
        assertEquals(SEED_STOCK, p.getStock(), "取消后库存应回补");
    }

    @Test
    @DisplayName("待付款订单取消：库存回补、销量保持种子值不变")
    void createdOrderCancelKeepsSales() throws Exception {
        MockHttpSession session = loginAsUser("salesuser3");

        mockMvc.perform(post("/cart/add")
                        .param("productId", String.valueOf(productId))
                        .param("quantity", "1")
                        .session(session))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/order/create")
                        .param("receiverName", "钱七")
                        .param("receiverPhone", "13600136000")
                        .param("receiverAddress", "杭州市西湖区测试路 5 号")
                        .session(session))
                .andExpect(status().is3xxRedirection());
        Long orderId = latestOrderId(session);

        MockHttpSession admin = new MockHttpSession();
        admin.setAttribute("userId", 999L);
        admin.setAttribute("role", UserRole.ADMIN);
        mockMvc.perform(post("/admin/order/" + orderId + "/cancel").session(admin))
                .andExpect(status().is3xxRedirection());

        Product p = productRepository.findById(productId).orElseThrow();
        assertEquals(SEED_SALES, p.getSales(), "待付款订单从未计销量，取消后不应变化");
        assertEquals(SEED_STOCK, p.getStock(), "取消后库存应回补");
    }

    /** 取当前用户最新一张订单的 id（测试辅助，每个用例只有一张）。 */
    private Long latestOrderId(MockHttpSession session) throws Exception {
        MvcResult orders = mockMvc.perform(get("/orders").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String html = orders.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("/orders/(\\d+)").matcher(html);
        if (!m.find()) {
            throw new AssertionError("订单页未找到任何订单链接");
        }
        return Long.parseLong(m.group(1));
    }
}
