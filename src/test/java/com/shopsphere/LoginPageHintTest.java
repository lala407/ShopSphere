// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：LoginPageHintTest —— 登录页测试账号提示与种子口令一致性（P2-11）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.service.LoginAttemptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录页「测试账号」提示测试（P2-11）。
 *
 * <p>{@code docs/截图清单.md} 第 07 张要求登录页是「登录表单 + 底部测试账号提示」，
 * 验收时老师照着页面提示就能登进来，不用回头翻 README。
 *
 * <p>只断言 HTML 里有那几行字是不够的：提示最容易出的问题是<b>页面写了、账号却登不进去</b>
 * （改过密码忘了同步页面，`docs/screenshots/07-登录页.png` 首版就写着旧口令）。
 * 所以这里多做一步——把页面上的「账号/口令」正则在下来，再去 {@code data.sql}
 * 把同名种子的 BCrypt 哈希捞出来，逐个用 BCrypt 比对：两边一致才算真的可用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginPageHintTest {

    /** 页面上的「管理员：<code>admin</code> / <code>admin123</code>」这种行。 */
    private static final Pattern HINT_LINE = Pattern.compile(
            "(管理员|普通用户)：<code>([^<]+)</code>\\s*/\\s*<code>([^<]+)</code>");

    /** data.sql 里的一条 INSERT ... VALUES ( ... )，用非贪婪 + DOTALL 抓括号内容。 */
    private static final Pattern VALUES_BLOCK = Pattern.compile("VALUES\\s*\\((.*?)\\)", Pattern.DOTALL);

    /** SQL 里的字符串字面量（种子数据没有转义单引号，够用）。 */
    private static final Pattern SQL_STRING = Pattern.compile("'([^']*)'");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    @AfterEach
    void resetLoginAttempts() {
        // 用例 3 会故意输错密码，不清零会影响同上下文里的其他登录测试
        loginAttemptService.reset();
    }

    @Test
    @DisplayName("登录页底部给出两组测试账号，验收不用翻 README")
    void loginPageShowsTestAccounts() throws Exception {
        String html = loginPage();

        assertTrue(html.contains("测试账号"), "登录页必须有「测试账号」提示区块");
        assertTrue(html.contains("管理员：<code>admin</code> / <code>admin123</code>"),
                "管理员账号提示缺失或被改动");
        assertTrue(html.contains("普通用户：<code>demo</code> / <code>demo123</code>"),
                "普通用户提示缺失或被改动");
        // 截图清单要求「灰色小字」：Bootstrap 的 text-muted + small
        assertTrue(html.contains("text-muted small"), "提示应为灰色小字（text-muted small）");
    }

    @Test
    @DisplayName("提示里的口令与 data.sql 种子账号的 BCrypt 哈希一致")
    void hintedPasswordsMatchSeededAccounts() throws Exception {
        Map<String, String> hints = parseHints(loginPage());
        Map<String, String> seeds = parseSeedHashes();

        assertEquals(2, hints.size(), "应解析出管理员与普通用户两组账号");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        hints.forEach((username, password) -> {
            String hash = seeds.get(username);
            assertNotNull(hash, "data.sql 里没有种子账号 " + username + "，页面提示会失效");
            assertTrue(encoder.matches(password, hash),
                    "页面提示的口令与 " + username + " 的种子哈希不匹配——页面写了旧密码，验收会登不进去");
        });
    }

    @Test
    @DisplayName("密码输错时错误提示与测试账号提示同时可见")
    void hintStillVisibleWhenLoginFails() throws Exception {
        String html = mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "definitely-wrong"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("alert-danger"), "输错密码应有红色错误提示");
        assertTrue(html.contains("测试账号"), "出错页面上也要能看到测试账号，否则老师一输错就找不到入口");
        assertTrue(html.contains("admin123"), "出错后提示内容不应丢失");
    }

    // ---------- 辅助 ----------

    private String loginPage() throws Exception {
        return mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** 从登录页 HTML 里解析「用户名 → 口令」。 */
    private Map<String, String> parseHints(String html) {
        Map<String, String> hints = new LinkedHashMap<>();
        Matcher m = HINT_LINE.matcher(html);
        while (m.find()) {
            hints.put(m.group(2), m.group(3));
        }
        return hints;
    }

    /** 从 data.sql 里解析「用户名 → BCrypt 哈希」，只认带 $2a/$2b 哈希的 INSERT 块。 */
    private Map<String, String> parseSeedHashes() throws Exception {
        String sql = new ClassPathResource("data.sql").getContentAsString(StandardCharsets.UTF_8);
        Map<String, String> seeds = new LinkedHashMap<>();
        Matcher block = VALUES_BLOCK.matcher(sql);
        while (block.find()) {
            List<String> literals = new ArrayList<>();
            Matcher str = SQL_STRING.matcher(block.group(1));
            while (str.find()) {
                literals.add(str.group(1));
            }
            String hash = literals.stream()
                    .filter(v -> v.startsWith("$2a$") || v.startsWith("$2b$") || v.startsWith("$2y$"))
                    .findFirst().orElse(null);
            if (hash != null && !literals.isEmpty()) {
                seeds.put(literals.get(0), hash);   // INSERT 的第一列就是 username
            }
        }
        return seeds;
    }
}
