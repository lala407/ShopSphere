// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：CsrfProtectionTest —— CSRF 令牌校验专项测试（P2-5）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.entity.User;
import com.shopsphere.filter.CsrfTokenFilter;
import com.shopsphere.repository.CartItemRepository;
import com.shopsphere.repository.OrderItemRepository;
import com.shopsphere.repository.OrderRepository;
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

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF 防护测试（P2-5）。
 *
 * <p>测试 profile 默认关掉了 CSRF（见 application-test.yml，否则几十个用例都要先取 token），
 * 这里用 {@code properties} 单独打开，只针对防护本身做验证：
 * <ul>
 *   <li>页面能拿到 token（meta 标签 + Session 属性）；</li>
 *   <li>不带 token / 带错 token 的 POST 一律 403；</li>
 *   <li>带对 token 的 POST 正常放行（登录、注册两条真实链路）；</li>
 *   <li>GET 不受影响。</li>
 * </ul>
 */
@SpringBootTest(properties = "shopsphere.security.csrf.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CsrfProtectionTest {

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
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("GET 页面下发令牌：Session 里有，页面 meta 里也有；GET 请求本身不受限制")
    void tokenIssuedOnGet() throws Exception {
        MvcResult result = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session, "首次访问应建立会话以承载令牌");
        String token = (String) session.getAttribute(CsrfTokenFilter.SESSION_KEY);
        assertNotNull(token, "Session 里应有 csrfToken");
        assertEquals(36, token.length(), "UUID 形式，36 字符");

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("name=\"_csrf\""), "页面应输出 meta[name=_csrf] 供脚本读取");
        assertTrue(html.contains(token), "meta 的 content 应为会话里的令牌值");
    }

    @Test
    @DisplayName("POST 缺令牌 / 令牌错误 → 403，且不产生任何副作用")
    void postWithoutValidTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "csrfuser")
                        .param("password", "abc123456")
                        .param("email", "csrf@example.com"))
                .andExpect(status().isForbidden());
        assertTrue(userRepository.findByUsername("csrfuser").isEmpty(), "被拦截的请求不应落库");

        // 带一个错误令牌也不行
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CsrfTokenFilter.SESSION_KEY, "server-side-token");
        mockMvc.perform(post("/register")
                        .session(session)
                        .param("username", "csrfuser")
                        .param("password", "abc123456")
                        .param("email", "csrf@example.com")
                        .param("_csrf", "attacker-guess"))
                .andExpect(status().isForbidden());
        assertTrue(userRepository.findByUsername("csrfuser").isEmpty());
    }

    @Test
    @DisplayName("带正确令牌：注册与登录都能通过")
    void postWithValidTokenPasses() throws Exception {
        // 注册
        MockHttpSession regSession = fetchTokenSession("/register");
        String token = (String) regSession.getAttribute(CsrfTokenFilter.SESSION_KEY);
        mockMvc.perform(post("/register")
                        .session(regSession)
                        .param("_csrf", token)
                        .param("username", "csrfuser")
                        .param("password", "abc123456")
                        .param("email", "csrf@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        User saved = userRepository.findByUsername("csrfuser").orElseThrow();
        assertEquals("csrf@example.com", saved.getEmail());

        // 登录（注册会话里的令牌不再有效？令牌仍在该会话，直接复用）
        MockHttpSession loginSession = fetchTokenSession("/login");
        String loginToken = (String) loginSession.getAttribute(CsrfTokenFilter.SESSION_KEY);
        mockMvc.perform(post("/login")
                        .session(loginSession)
                        .param("_csrf", loginToken)
                        .param("username", "csrfuser")
                        .param("password", "abc123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    /** 打开某个页面，取回承载令牌的会话。 */
    private MockHttpSession fetchTokenSession(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
