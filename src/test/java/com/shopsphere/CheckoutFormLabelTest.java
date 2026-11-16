// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：CheckoutFormLabelTest —— 结算页收货表单 label 回归测试（P1-5）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.common.ProductStatus;
import com.shopsphere.entity.Category;
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
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 结算页表单可访问性回归测试（H2 内存库）。
 *
 * <p>对应缺陷清单 P1-5：修复前 {@code checkout.html} 的收货表单只有裸
 * {@code <input>}，没有 {@code <label>}，页面看起来像半成品，屏幕阅读器也读不出字段名。
 * 修复后要求：
 * <ol>
 *   <li>四个字段（收货人 / 手机号 / 收货地址 / 备注）都有 {@code <label for="xxx">}；
 *   <li>每个 label 的 {@code for} 都能在页面里找到同名 {@code id} 的控件（关联成立）；
 *   <li>{@code name} 属性保持不变，避免改动破坏 {@code /order/create} 的绑定。
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutFormLabelTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    /** 四个字段：label for 值 = input id 值 = 提交参数名。 */
    private static final String[] FIELDS = {"receiverName", "receiverPhone", "receiverAddress", "remark"};

    @BeforeEach
    void setUp() {
        // 外键顺序：明细 → 订单 → 购物车 → 商品 → 分类
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = new Category();
        category.setName("表单测试分类");
        category.setSortOrder(1);
        categoryRepository.save(category);

        Product p = new Product();
        p.setName("表单测试商品");
        p.setDescription("P1-5 测试商品");
        p.setPrice(new BigDecimal("12.00"));
        p.setStock(5);
        p.setCategoryId(category.getId());
        p.setImageUrl("");
        p.setStatus(ProductStatus.ON_SALE);
        p.setSales(0);
        productRepository.save(p);
    }

    @Test
    @DisplayName("结算页四个收货字段都有 label，且 for 与控件 id 一一对应")
    void checkoutFormHasLabels() throws Exception {
        MvcResult login = mockMvc.perform(post("/register")
                        .param("username", "label_user")
                        .param("password", "123456")
                        .param("email", "label_user@example.com"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        login.getRequest().getSession(false);

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login")
                        .param("username", "label_user")
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession(false);

        // 购物车必须有商品，否则 /checkout 会重定向回购物车
        mockMvc.perform(post("/cart/add")
                        .param("productId", productRepository.findAll().get(0).getId().toString())
                        .param("quantity", "1")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        String html = mockMvc.perform(get("/checkout").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 1) 四个 label 都在，且总数为 4（不多不少）
        assertEquals(4, StringUtils.countOccurrencesOf(html, "class=\"form-label\""),
                "结算页应有且仅有 4 个 label（收货人/手机号/地址/备注）");

        for (String f : FIELDS) {
            assertTrue(html.contains("for=\"" + f + "\""), f + " 缺少 label for 属性");
            assertTrue(html.contains("id=\"" + f + "\""), f + " 缺少对应控件 id");
            assertTrue(html.contains("name=\"" + f + "\""), f + " 的 name 属性被改动，会破坏表单绑定");
            // for 值与 id 值必须完全相同才算关联成立，这里已由上面两条同时成立保证
        }

        // 2) 反向校验：每个 id 只能被一个 label 引用，防止复制粘贴串了
        Matcher m = Pattern.compile("for=\"([A-Za-z]+)\"").matcher(html);
        int labelCount = 0;
        while (m.find()) {
            labelCount++;
            assertTrue(m.group(1).startsWith("receiver") || m.group(1).equals("remark"),
                    "出现意料之外的 label for=" + m.group(1));
        }
        assertEquals(4, labelCount, "label for 数量应为 4");
    }
}
