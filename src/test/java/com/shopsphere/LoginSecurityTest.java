// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：LoginSecurityTest —— 登录会话轮换与失败次数限制（P2-5）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.repository.CartItemRepository;
import com.shopsphere.repository.OrderItemRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.UserRepository;
import com.shopsphere.service.LoginAttemptService;
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录安全测试（P2-5）。覆盖两个与"会话"相关的防护：
 *
 * <ol>
 *   <li><b>Session 轮换</b>：登录成功后旧会话必须被作废、换一个新 ID，
 *       否则 session fixation 攻击成立（攻击者预置 JSESSIONID，受害者登录后该 ID 就是已登录态）；</li>
 *   <li><b>失败次数限制</b>：同一用户名连错 5 次锁 5 分钟，锁定期间即使密码正确也拒绝。</li>
 * </ol>
 *
 * <p>限流器是单例 Bean，测试之间会互相影响，因此 {@code @BeforeEach} 里 reset。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginSecurityTest {

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

    @Autowired
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() throws Exception {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        userRepository.deleteAll();
        loginAttemptService.reset();

        mockMvc.perform(post("/register")
                        .param("username", "secuser")
                        .param("password", "abc123456")
                        .param("email", "sec@example.com"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("登录成功后作废旧会话、换新 ID，登录态写在新会话上")
    void sessionIsRotatedOnLogin() throws Exception {
        MockHttpSession oldSession = new MockHttpSession();
        String oldId = oldSession.getId();
        oldSession.setAttribute("attackerPlanted", "yes");   // 模拟攻击者预置的会话数据

        MvcResult result = mockMvc.perform(post("/login")
                        .session(oldSession)
                        .param("username", "secuser")
                        .param("password", "abc123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        assertTrue(oldSession.isInvalid(), "登录成功后旧会话必须作废（防 session fixation）");

        MockHttpSession newSession = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(newSession, "应建立新会话携带登录态");
        assertNotSame(oldSession, newSession);
        assertTrue(!oldId.equals(newSession.getId()), "会话 ID 必须变化");
        assertEquals("secuser", newSession.getAttribute("username"));
        assertNotNull(newSession.getAttribute("userId"));
        // 旧会话里预置的数据不会带进新会话
        assertEquals(null, newSession.getAttribute("attackerPlanted"));
    }

    @Test
    @DisplayName("连错 5 次后锁定：即使密码正确也拒绝，并提示剩余时间")
    void accountLockedAfterFiveFailures() throws Exception {
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/login")
                            .param("username", "secuser")
                            .param("password", "wrong-" + i))
                    .andExpect(status().isOk());
        }

        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", "secuser")
                        .param("password", "abc123456"))   // 正确密码
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("登录失败次数过多"), "锁定期间应提示次数过多，实际页面：" + html.length());
        assertTrue(html.contains("分钟后再试"), "提示应给出等待时间");
    }

    @Test
    @DisplayName("登录成功会清零失败计数：错 3 次后成功，再错 1 次不会被锁")
    void successResetsFailureCounter() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/login")
                            .param("username", "secuser")
                            .param("password", "wrong-" + i))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/login")
                        .param("username", "secuser")
                        .param("password", "abc123456"))
                .andExpect(status().is3xxRedirection());

        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", "secuser")
                        .param("password", "wrong-again"))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("用户名或密码错误"), "计数已清零，应是普通错误提示");
        assertTrue(!html.contains("登录失败次数过多"), "不应被锁定");
    }
}
