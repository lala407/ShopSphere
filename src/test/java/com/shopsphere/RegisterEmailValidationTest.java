// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：RegisterEmailValidationTest —— 注册邮箱必填与格式校验回归测试（P2-3）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.entity.User;
import com.shopsphere.repository.CartItemRepository;
import com.shopsphere.repository.OrderItemRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.UserRepository;
import jakarta.validation.ConstraintViolationException;
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

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注册邮箱校验集成测试（H2 内存库）。
 *
 * <p>对应缺陷清单 P2-3：注册邮箱原本"不校验格式、非必填"。
 * 现状是三层防护，本测试逐层验证：
 * <ol>
 *   <li>前端：{@code register.html} 的 email 输入框 {@code type="email" required maxlength="100"}；</li>
 *   <li>Service：{@code UserService#register} 必填判断 + 正则 + 长度 ≤100，抛中文
 *       {@link IllegalArgumentException} 由 Controller 落成页面红字（不是 400 白页）；</li>
 *   <li>实体：{@link User#getEmail()} 上的 {@code @Email}/{@code @Size}，
 *       绕过 Service 直接 save 也写不进脏格式。</li>
 * </ol>
 *
 * <p>为什么邮箱必须必填：订单确认、付款成功、发货通知全靠它，空邮箱等于用户收不到任何通知。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegisterEmailValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @BeforeEach
    void setUp() {
        // 外键顺序：明细 → 订单 → 购物车 → 用户
        // 全量跑时前面的测试会留下引用 user 的订单/购物车，漏删会触发 FK 违规（P2-2 踩过的坑）
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("注册页：邮箱输入框为 type=email + required + maxlength，且标注『必填』")
    void registerFormMarksEmailRequired() throws Exception {
        MvcResult result = mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        int idx = html.indexOf("id=\"email\"");
        assertTrue(idx > 0, "注册页应有 email 输入框");
        String inputTag = html.substring(html.lastIndexOf("<input", idx), html.indexOf(">", idx) + 1);
        assertTrue(inputTag.contains("type=\"email\""), "应为 type=email 触发浏览器格式校验：" + inputTag);
        assertTrue(inputTag.contains("required"), "邮箱应为必填：" + inputTag);
        assertTrue(inputTag.contains("maxlength=\"100\""), "应与列宽一致限制 100 字符：" + inputTag);
        assertTrue(html.contains("用于接收订单确认与发货通知"), "应说明邮箱用途");
    }

    @Test
    @DisplayName("注册成功：邮箱前后空格被 trim，密码以 BCrypt 哈希落库")
    void registerSucceeds() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "zhangsan")
                        .param("password", "abc123456")
                        .param("email", "  ZhangSan@Example.com  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        User saved = userRepository.findByUsername("zhangsan").orElseThrow();
        assertEquals("ZhangSan@Example.com", saved.getEmail(), "前后空格应被 trim");
        assertFalse(saved.getPassword().contains("abc123456"), "密码不能明文落库");
        assertTrue(saved.getPassword().startsWith("$2a$") || saved.getPassword().startsWith("$2b$"),
                "应为 BCrypt 哈希：" + saved.getPassword());
    }

    @Test
    @DisplayName("邮箱缺失或纯空白：留在注册页并提示必填，不落库")
    void blankEmailRejected() throws Exception {
        // 完全不传 email
        assertRegisterError("lisi", "abc123456", null, "邮箱不能为空");
        // 传空串
        assertRegisterError("lisi", "abc123456", "", "邮箱不能为空");
        // 纯空格（trim 后为空）
        assertRegisterError("lisi", "abc123456", "    ", "邮箱不能为空");

        assertTrue(userRepository.findByUsername("lisi").isEmpty(), "校验失败不应落库");
    }

    @Test
    @DisplayName("邮箱格式错误：缺 @ / 缺域名点 / 内嵌空格 / 超 100 字符，全部被拒")
    void malformedEmailRejected() throws Exception {
        String[] bad = {
                "zhangsan.example.com",            // 没有 @
                "zhangsan@example",                // 域名没有点
                "zhang san@example.com",           // 内嵌空格
                "@example.com",                    // 没有本地部分
                "zhangsan@.com",                   // 域名段为空
                "zhangsan@example.c"               // 顶级域只有 1 位
        };
        for (String email : bad) {
            assertRegisterError("wangwu", "abc123456", email, "邮箱格式不正确");
        }
        // 超长（本地部分 95 + @ + 域名 12 > 100）
        String tooLong = "a".repeat(95) + "@example.com";
        assertTrue(tooLong.length() > 100);
        assertRegisterError("wangwu", "abc123456", tooLong, "邮箱格式不正确");

        assertTrue(userRepository.findByUsername("wangwu").isEmpty(), "校验失败不应落库");
    }

    @Test
    @DisplayName("用户名重复与密码过短：分别给出对应中文提示")
    void otherRegisterRulesStillHold() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "zhaoliu")
                        .param("password", "abc123456")
                        .param("email", "zhaoliu@example.com"))
                .andExpect(status().is3xxRedirection());

        // 重名
        assertRegisterError("zhaoliu", "abc123456", "other@example.com", "用户名已被占用");
        // 密码过短
        assertRegisterError("newuser", "123", "newuser@example.com", "密码长度需在 6-20 字符之间");
        // 用户名过短
        assertRegisterError("ab", "abc123456", "ab@example.com", "用户名长度需在 3-20 字符之间");

        assertEquals(1, userRepository.count(), "只有第一个合法用户应落库");
    }

    @Test
    @DisplayName("实体层兜底：绕过 Service 直接 save 脏邮箱，被 Bean Validation 拦下")
    void entityLevelEmailGuard() {
        User dirty = new User();
        dirty.setUsername("bypass");
        dirty.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        dirty.setEmail("not-an-email");
        dirty.setRole("CUSTOMER");
        dirty.setStatus(1);

        assertThrows(ConstraintViolationException.class, () -> userRepository.saveAndFlush(dirty),
                "实体上的 @Email 应在 flush 时报错，防止绕过 Service 写入脏数据");
    }

    /** 提交注册并断言「回到注册页 + 页面含指定错误提示 + 未跳转」。 */
    private void assertRegisterError(String username, String password, String email, String expectMsg)
            throws Exception {
        var request = post("/register")
                .param("username", username)
                .param("password", password);
        if (email != null) {
            request = request.param("email", email);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains(expectMsg),
                "应提示「" + expectMsg + "」，实际页面未包含。" );
        assertTrue(html.contains("alert-danger"), "错误应渲染成红色提示块");
    }
}
