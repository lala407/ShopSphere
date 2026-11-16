// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AdminCategoryTest —— 后台分类管理回归测试（P1-8）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.common.ProductStatus;
import com.shopsphere.common.UserRole;
import com.shopsphere.entity.Category;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后台分类管理集成测试（H2 内存库）。
 *
 * <p>对应缺陷清单 P1-8：修复前新增分类只能改 data.sql 再重启，没有管理页面。
 * 修复后 {@code /admin/category} 提供：列表（含商品数）/ 新增 / 改名 / 排序 / 删除（引用校验）。
 *
 * <p>数据构造：「电子产品」（sortOrder=1，下挂 1 件商品）+「旧分类」（sortOrder=2，无商品）——
 * 正好覆盖"有商品删不掉 / 没商品能删"两条路径。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCategoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    private Long techId;
    private Long emptyId;

    @BeforeEach
    void setUp() {
        // 外键顺序：明细 → 订单 →（购物车）→ 商品 → 分类 → 用户
        // 订单引用 user，漏删会导致全量跑时 userRepository.deleteAll() 违反 FK
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        LocalDateTime now = LocalDateTime.of(2026, 11, 16, 17, 0);

        User admin = new User();
        admin.setUsername("cat_admin");
        admin.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        admin.setEmail("cat_admin@example.com");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(1);
        admin.setCreatedAt(now);
        userRepository.save(admin);

        Category tech = new Category();
        tech.setName("电子产品");
        tech.setSortOrder(1);
        tech = categoryRepository.save(tech);
        techId = tech.getId();

        Category empty = new Category();
        empty.setName("旧分类");
        empty.setSortOrder(2);
        empty = categoryRepository.save(empty);
        emptyId = empty.getId();

        Product p = new Product();
        p.setName("蓝牙耳机");
        p.setPrice(new BigDecimal("99.00"));
        p.setStock(10);
        p.setCategoryId(techId);
        p.setStatus(ProductStatus.ON_SALE);
        productRepository.save(p);
    }

    private MockHttpSession adminSession() {
        MockHttpSession admin = new MockHttpSession();
        admin.setAttribute("userId", 999L);
        admin.setAttribute("role", UserRole.ADMIN);
        return admin;
    }

    @Test
    @DisplayName("列表页：按 sortOrder 升序渲染分类、model 带商品计数、页签高亮")
    @SuppressWarnings("unchecked")
    void listRendersCategoriesWithCounts() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/category").session(adminSession()))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("电子产品"), "应渲染出分类名");
        assertTrue(html.contains("旧分类"), "应渲染出第二个分类");
        assertTrue(html.contains("/admin/category/add"), "应包含新增表单");
        assertTrue(html.contains("/admin/category/" + techId + "/delete"), "应包含删除表单");
        assertTrue(html.contains("nav-link active"), "后台页签应渲染出高亮样式");

        // 商品计数直接看 model：电子产品=1、旧分类=0
        java.util.Map<Long, Long> counts =
                (java.util.Map<Long, Long>) result.getModelAndView().getModel().get("productCounts");
        assertEquals(1L, counts.get(techId), "电子产品分类应计 1 件商品");
        assertEquals(0L, counts.get(emptyId), "旧分类应计 0 件商品");

        // 列表按 sortOrder 升序：电子产品在前
        List<Category> rendered = (List<Category>) result.getModelAndView().getModel().get("categories");
        assertEquals(techId, rendered.get(0).getId(), "sortOrder=1 的分类应排第一");
    }

    @Test
    @DisplayName("新增：成功创建；重名与空名被拒；排序值生效")
    void addCategory() throws Exception {
        mockMvc.perform(post("/admin/category/add").session(adminSession())
                        .param("name", "数码配件").param("sortOrder", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/category"))
                .andExpect(flash().attribute("msg", "分类「数码配件」已创建"));

        List<Category> all = categoryRepository.findAllByOrderBySortOrderAsc();
        assertEquals(3, all.size(), "应有 3 个分类");
        // sortOrder 1 → 2 → 5：新增的排最后
        assertEquals("电子产品", all.get(0).getName());
        assertEquals("旧分类", all.get(1).getName());
        assertEquals("数码配件", all.get(2).getName());

        // 重名被拒
        long before = categoryRepository.count();
        mockMvc.perform(post("/admin/category/add").session(adminSession())
                        .param("name", "电子产品").param("sortOrder", "9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "分类「电子产品」已存在"));
        assertEquals(before, categoryRepository.count(), "重名分类不应落库");

        // 空名（纯空格）被拒
        mockMvc.perform(post("/admin/category/add").session(adminSession())
                        .param("name", "   ").param("sortOrder", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "分类名不能为空"));
        assertEquals(before, categoryRepository.count());
    }

    @Test
    @DisplayName("改名+排序：一起提交生效；改成别人在用的名字被拒；只调排序不受重名影响")
    void updateCategory() throws Exception {
        mockMvc.perform(post("/admin/category/{id}/update", emptyId).session(adminSession())
                        .param("name", "家居日用").param("sortOrder", "9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/category"))
                .andExpect(flash().attribute("msg", "分类「家居日用」已更新"));

        Category updated = categoryRepository.findById(emptyId).orElseThrow();
        assertEquals("家居日用", updated.getName(), "改名应生效");
        assertEquals(9, updated.getSortOrder(), "排序应生效");

        // 改成「电子产品」被拒（别的分类在用）
        mockMvc.perform(post("/admin/category/{id}/update", emptyId).session(adminSession())
                        .param("name", "电子产品").param("sortOrder", "9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "分类名「电子产品」已被其他分类使用"));
        assertEquals("家居日用", categoryRepository.findById(emptyId).orElseThrow().getName(),
                "被拒后名字不应被改掉");

        // 名字保持不变、只调排序 → findByName 命中自己，必须放行
        mockMvc.perform(post("/admin/category/{id}/update", emptyId).session(adminSession())
                        .param("name", "家居日用").param("sortOrder", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("msg", "分类「家居日用」已更新"));
        assertEquals(1, categoryRepository.findById(emptyId).orElseThrow().getSortOrder());
    }

    @Test
    @DisplayName("删除：有商品引用的分类被拒并提示数量；空分类删除成功")
    void deleteCategory() throws Exception {
        // 「电子产品」下有 1 件商品 → 拒绝
        mockMvc.perform(post("/admin/category/{id}/delete", techId).session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/category"))
                .andExpect(flash().attribute("error",
                        "分类「电子产品」下还有 1 件商品，请先移走或删除商品再删除分类"));
        assertTrue(categoryRepository.findById(techId).isPresent(), "被引用的分类必须还在");

        // 把商品移走后再删 → 成功
        mockMvc.perform(post("/admin/category/{id}/delete", emptyId).session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("msg", "分类「旧分类」已删除"));
        assertFalse(categoryRepository.findById(emptyId).isPresent(), "空分类应被物理删除");
        assertEquals(1, categoryRepository.count(), "应只剩「电子产品」一个分类");

        // 不存在的分类 → 全局异常处理兜底
        mockMvc.perform(post("/admin/category/{id}/delete", 99999L).session(adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "分类不存在"));
    }
}
