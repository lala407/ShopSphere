// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AdminProductDeleteTest —— 后台商品物理删除回归测试（P1-7）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.common.OrderStatus;
import com.shopsphere.common.ProductStatus;
import com.shopsphere.common.UserRole;
import com.shopsphere.entity.CartItem;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Order;
import com.shopsphere.entity.OrderItem;
import com.shopsphere.entity.Product;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后台商品删除集成测试（H2 内存库）。
 *
 * <p>对应缺陷清单 P1-7：修复前后台只有"上下架"，没有"删除"按钮，
 * 与作业页 ex.html 写的"添加、删除、修改等操作"字面对不上。
 * 修复后：{@code POST /admin/product/{id}/delete} ——
 * <ul>
 *   <li>从未被订单买过 → 物理删除（并清掉购物车里的引用）+ 成功提示；</li>
 *   <li>已被订单买过 → 拒绝删除，提示改用下架（保护订单历史与统计口径）。</li>
 * </ul>
 *
 * <p>数据构造：两个商品 ——
 * 「全新商品」没有任何订单引用（挂在某用户购物车里，验证引用一并清理）；
 * 「老商品」被一张订单的明细引用过，必须删不掉。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminProductDeleteTest {

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

    private Long freshProductId;
    private Long oldProductId;

    @BeforeEach
    void setUp() {
        // 外键顺序：明细 → 订单 → 购物车 → 商品 → 分类 → 用户
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        LocalDateTime now = LocalDateTime.of(2026, 11, 16, 10, 0);

        // 测试用户：既是下单人，也是购物车主
        User buyer = new User();
        buyer.setUsername("del_buyer");
        buyer.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        buyer.setEmail("del_buyer@example.com");
        buyer.setRole(UserRole.CUSTOMER);
        buyer.setStatus(1);
        buyer.setCreatedAt(now);
        buyer = userRepository.save(buyer);

        Category cat = new Category();
        cat.setName("测试分类");
        cat.setSortOrder(1);
        cat = categoryRepository.save(cat);

        // 商品 A「全新商品」：从未被买过（允许物理删除）
        Product fresh = new Product();
        fresh.setName("全新商品");
        fresh.setPrice(new BigDecimal("19.90"));
        fresh.setStock(50);
        fresh.setCategoryId(cat.getId());
        fresh.setStatus(ProductStatus.ON_SALE);
        fresh = productRepository.save(fresh);
        freshProductId = fresh.getId();

        // 商品 B「老商品」：被订单明细引用过（必须拒绝删除）
        Product old = new Product();
        old.setName("老商品");
        old.setPrice(new BigDecimal("99.00"));
        old.setStock(10);
        old.setCategoryId(cat.getId());
        old.setStatus(ProductStatus.ON_SALE);
        old = productRepository.save(old);
        oldProductId = old.getId();

        // 订单 + 明细引用商品 B
        Order order = new Order();
        order.setOrderNo("ORDDELTEST0001");
        order.setUser(buyer);
        order.setTotalAmount(new BigDecimal("99.00"));
        order.setStatus(OrderStatus.PAID);
        order.setReceiverName("收货人");
        order.setReceiverPhone("13800138000");
        order.setReceiverAddress("广州市天河区删除测试路 1 号");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order = orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductId(oldProductId);
        item.setProductName(old.getName());
        item.setProductPrice(old.getPrice());
        item.setQuantity(1);
        item.setSubtotal(old.getPrice());
        orderItemRepository.save(item);

        // 购物车里挂一件商品 A（物理删除 A 时应被一并清掉）
        CartItem cartItem = new CartItem();
        cartItem.setUser(buyer);
        cartItem.setProduct(fresh);
        cartItem.setQuantity(2);
        cartItem.setCreatedAt(now);
        cartItem.setUpdatedAt(now);
        cartItemRepository.save(cartItem);
    }

    private MockHttpSession adminSession() {
        MockHttpSession admin = new MockHttpSession();
        admin.setAttribute("userId", 999L);
        admin.setAttribute("role", UserRole.ADMIN);
        return admin;
    }

    @Test
    @DisplayName("删除从未被买过的商品：物理删除成功，购物车引用一并清掉")
    void deleteUnreferencedProduct() throws Exception {
        mockMvc.perform(post("/admin/product/{id}/delete", freshProductId).session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/product"))
                .andExpect(flash().attribute("msg",
                        "商品「全新商品」已删除"));

        assertFalse(productRepository.findById(freshProductId).isPresent(),
                "商品应已从数据库物理删除");
        assertTrue(cartItemRepository.findAll().isEmpty(),
                "购物车里对该商品的引用应被一并清理");
    }

    @Test
    @DisplayName("删除已被订单引用的商品：拒绝删除并提示改用下架")
    void deleteReferencedProductRejected() throws Exception {
        mockMvc.perform(post("/admin/product/{id}/delete", oldProductId).session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/product"))
                .andExpect(flash().attribute("error",
                        "商品「老商品」已有订单记录，删除会影响历史订单与统计，请改用下架"));

        assertTrue(productRepository.findById(oldProductId).isPresent(),
                "被订单引用的商品必须还在数据库里");
        assertTrue(orderItemRepository.existsByProductId(oldProductId),
                "订单明细不受影响");
    }

    @Test
    @DisplayName("删除不存在的商品：走全局异常处理，flash 错误提示")
    void deleteNonexistentProduct() throws Exception {
        mockMvc.perform(post("/admin/product/{id}/delete", 99999L).session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "商品不存在"));

        assertEquals(2, productRepository.count(), "两个真实商品都不应被动过");
    }
}
