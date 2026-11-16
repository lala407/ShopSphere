// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：ProductDetailAccessTest —— 下架商品详情 404 回归测试（P1-1）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.common.ProductStatus;
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

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 下架商品访问控制测试（H2 内存库）。
 *
 * <p>对应缺陷清单 P1-1：修复前 {@code detail()} 只 {@code findById}，
 * 已下架商品拿到旧链接仍能打开详情页（加购按钮还在）。修复后要求：
 * <ol>
 *   <li>在售商品详情正常 200；</li>
 *   <li>已下架商品详情直接 404，与前台列表（searchOnSale）口径一致；</li>
 *   <li>兜底防线仍在：绕过页面直接 POST /cart/add 下架商品，会被 CartService 拦截。</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductDetailAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private Long onSaleId;
    private Long offShelfId;

    @BeforeEach
    void setUp() {
        cartItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = new Category();
        category.setName("测试分类");
        category.setSortOrder(1);
        categoryRepository.save(category);

        onSaleId = saveProduct(category.getId(), "在售商品", ProductStatus.ON_SALE);
        offShelfId = saveProduct(category.getId(), "下架商品", ProductStatus.OFF_SHELF);
    }

    private Long saveProduct(Long categoryId, String name, ProductStatus status) {
        Product p = new Product();
        p.setName(name);
        p.setDescription("P1-1 测试商品");
        p.setPrice(new BigDecimal("10.00"));
        p.setStock(5);
        p.setCategoryId(categoryId);
        p.setImageUrl("");
        p.setStatus(status);
        return productRepository.save(p).getId();
    }

    @Test
    @DisplayName("在售商品详情：200 正常打开")
    void onSaleDetailIsOk() throws Exception {
        mockMvc.perform(get("/product/" + onSaleId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("已下架商品详情：404，与前台列表口径一致（P1-1）")
    void offShelfDetailIs404() throws Exception {
        mockMvc.perform(get("/product/" + offShelfId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("兜底：直接 POST /cart/add 下架商品被 CartService 拦截，购物车为空")
    void offShelfCannotBeAddedToCart() throws Exception {
        // 注册并登录一个用户
        mockMvc.perform(post("/register")
                        .param("username", "p11user")
                        .param("password", "123456")
                        .param("email", "p11@example.com"))
                .andExpect(status().is3xxRedirection());
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login")
                        .param("username", "p11user")
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession(false);

        // 绕过详情页直接加购下架商品 → 重定向回详情页（带 flash 错误）
        mockMvc.perform(post("/cart/add")
                        .param("productId", String.valueOf(offShelfId))
                        .param("quantity", "1")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        assertEquals(0, cartItemRepository.count(), "下架商品不允许进入购物车");
    }
}
