// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AdminProductValidationTest —— 后台商品保存服务端校验回归测试（P2-2）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.common.ProductStatus;
import com.shopsphere.common.UserRole;
import com.shopsphere.entity.Category;
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
 * 后台商品保存服务端校验集成测试（H2 内存库）。
 *
 * <p>对应缺陷清单 P2-2：修复前 price 只靠前端 {@code min="0.01"}，
 * 用 curl 直接 POST 可以把负价格/负库存写进库（服务端完全不设防）。
 * 修复后：Product 实体加 Bean Validation 注解 + {@code @Valid} 绑定校验，
 * categoryId 额外查一次分类是否存在（裸 Long 无外键，数据库不会拦）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminProductValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private Long categoryId;

    @BeforeEach
    void setUp() {
        // 外键顺序：明细 → 订单 → 购物车 → 商品 → 分类 → 用户
        // ⚠️ 全量跑时前面的测试会留下订单（引用 user），漏删 orders 会导致
        // userRepository.deleteAll() 违反 FK（单跑发现不了，全量必炸）
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        User admin = new User();
        admin.setUsername("val_admin");
        admin.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        admin.setEmail("val_admin@example.com");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(1);
        admin.setCreatedAt(LocalDateTime.of(2026, 11, 16, 18, 0));
        userRepository.save(admin);

        Category cat = new Category();
        cat.setName("测试分类");
        cat.setSortOrder(1);
        cat = categoryRepository.save(cat);
        categoryId = cat.getId();
    }

    private MockHttpSession adminSession() {
        MockHttpSession admin = new MockHttpSession();
        admin.setAttribute("userId", 999L);
        admin.setAttribute("role", UserRole.ADMIN);
        return admin;
    }

    @Test
    @DisplayName("合法参数：正常落库（校验不误伤正常保存）")
    void validSavePersists() throws Exception {
        mockMvc.perform(post("/admin/product/save").session(adminSession())
                        .param("name", "无线鼠标")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("price", "59.90")
                        .param("stock", "100")
                        .param("status", ProductStatus.ON_SALE.name()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/product"))
                .andExpect(flash().attribute("msg", "保存成功"));

        Product saved = productRepository.findAll().get(0);
        assertEquals("无线鼠标", saved.getName());
        assertEquals(0, saved.getPrice().compareTo(new BigDecimal("59.90")));
        assertEquals(100, saved.getStock());
    }

    @Test
    @DisplayName("负价格被拒：直接 POST 不再能写进库")
    void negativePriceRejected() throws Exception {
        mockMvc.perform(post("/admin/product/save").session(adminSession())
                        .param("name", "负价格商品")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("price", "-5")
                        .param("stock", "10")
                        .param("status", ProductStatus.ON_SALE.name()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/product/new"))
                .andExpect(flash().attribute("error", "价格必须大于 0"));

        assertTrue(productRepository.findAll().isEmpty(), "负价格商品不应落库");
    }

    @Test
    @DisplayName("负库存被拒 + 空商品名被拒")
    void negativeStockAndBlankNameRejected() throws Exception {
        mockMvc.perform(post("/admin/product/save").session(adminSession())
                        .param("name", "负库存商品")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("price", "10.00")
                        .param("stock", "-3")
                        .param("status", ProductStatus.ON_SALE.name()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/product/new"))
                .andExpect(flash().attribute("error", "库存不能为负数"));
        assertTrue(productRepository.findAll().isEmpty());

        // 空名 + 合法其余字段：只有「商品名不能为空」一条
        mockMvc.perform(post("/admin/product/save").session(adminSession())
                        .param("name", "   ")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("price", "10.00")
                        .param("stock", "10")
                        .param("status", ProductStatus.ON_SALE.name()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "商品名不能为空"));
        assertTrue(productRepository.findAll().isEmpty());
    }

    @Test
    @DisplayName("不存在的分类被拒：裸 Long categoryId 无外键，服务端必须拦")
    void unknownCategoryRejected() throws Exception {
        mockMvc.perform(post("/admin/product/save").session(adminSession())
                        .param("name", "幽灵分类商品")
                        .param("categoryId", "99999")
                        .param("price", "10.00")
                        .param("stock", "10")
                        .param("status", ProductStatus.ON_SALE.name()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/product/new"))
                .andExpect(flash().attribute("error", "所选分类不存在，请重新选择"));
        assertTrue(productRepository.findAll().isEmpty(), "挂在不存在分类下的商品不应落库");
    }

    @Test
    @DisplayName("编辑路径校验失败回退到编辑表单；非数字价格被绑定层拦截")
    void editPathAndTypeMismatch() throws Exception {
        // 先造一个合法商品
        Product p = new Product();
        p.setName("老商品");
        p.setPrice(new BigDecimal("20.00"));
        p.setStock(5);
        p.setCategoryId(categoryId);
        p.setStatus(ProductStatus.ON_SALE);
        p = productRepository.save(p);

        // 编辑成负价格：应回退到该商品的编辑表单
        mockMvc.perform(post("/admin/product/save").session(adminSession())
                        .param("id", String.valueOf(p.getId()))
                        .param("name", "老商品")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("price", "-1")
                        .param("stock", "5")
                        .param("status", ProductStatus.ON_SALE.name()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/product/" + p.getId() + "/edit"))
                .andExpect(flash().attribute("error", "价格必须大于 0"));

        // 价格传非数字：类型绑定失败也走同一防线（不会变成 500）
        mockMvc.perform(post("/admin/product/save").session(adminSession())
                        .param("name", "乱填价格")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("price", "abc")
                        .param("stock", "10")
                        .param("status", ProductStatus.ON_SALE.name()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/product/new"));
        assertFalse(productRepository.findAll().stream()
                        .anyMatch(x -> "乱填价格".equals(x.getName())),
                "非数字价格不应落库");
    }
}
