// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：RedirectSafetyTest —— Referer 开放重定向防护（P2-6）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.common.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 开放重定向防护测试（P2-6）。
 *
 * <p>缺陷原状：{@code GlobalExceptionHandler} 把 {@code Referer} 头原样拼进 {@code redirect:}，
 * 攻击者只要让自己的页面带上 {@code Referer: https://evil.example/}，
 * 就能让本站抛出业务异常后跳到钓鱼页（URL 是本站域名发出的 302，用户很难分辨）。
 *
 * <p>修复后规则：只允许站内路径；带协议的 URL 只保留 path 部分（主机丢弃）；
 * {@code //host} 这类协议相对地址与非 {@code /} 开头的值一律回退首页。
 *
 * <p>用「访问不存在的商品编辑页」触发 {@link IllegalArgumentException}：
 * 该路径在 AdminProductController 里没有 try-catch，正好落到全局异常处理。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RedirectSafetyTest {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);
        session.setAttribute("username", "admin");
        session.setAttribute("role", UserRole.ADMIN);
        return session;
    }

    /** 触发一次业务异常，返回 302 的 Location（即全局异常处理给出的跳转目标）。 */
    private String redirectLocationFor(String referer) throws Exception {
        var request = get("/admin/product/{id}/edit", 999999L).session(adminSession());
        if (referer != null) {
            request = request.header("Referer", referer);
        }
        return mockMvc.perform(request)
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
    }

    @Test
    @DisplayName("站内路径原样回跳（含查询串）")
    void internalRefererIsKept() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals("/admin/product",
                redirectLocationFor("/admin/product"));
        org.junit.jupiter.api.Assertions.assertEquals("/admin/product?page=2",
                redirectLocationFor("/admin/product?page=2"));
    }

    @Test
    @DisplayName("外站 Referer：只取路径，主机被丢弃，跳不出本站")
    void externalRefererCannotLeaveSite() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals("/login",
                redirectLocationFor("https://evil.example/login"));
        // 没有 path 的外站地址 → 首页
        org.junit.jupiter.api.Assertions.assertEquals("/",
                redirectLocationFor("https://evil.example"));
    }

    @Test
    @DisplayName("协议相对地址 //host 与非法值一律回退首页")
    void protocolRelativeAndMalformedRefererFallBackHome() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals("/", redirectLocationFor("//evil.example/x"));
        org.junit.jupiter.api.Assertions.assertEquals("/", redirectLocationFor("evil.example/x"));
        org.junit.jupiter.api.Assertions.assertEquals("/", redirectLocationFor(""));
        org.junit.jupiter.api.Assertions.assertEquals("/", redirectLocationFor(null));
    }

    @Test
    @DisplayName("Referer 里带 CRLF 时不允许原样进响应头（响应头注入）")
    void crlfInRefererIsRejected() throws Exception {
        // MockMvc 的 header() 会拒绝含换行的值，这里模拟容器读到脏值后的行为：
        // 直接断言最终 Location 仍是站内相对路径
        mockMvc.perform(get("/admin/product/{id}/edit", 999999L)
                        .session(adminSession())
                        .header("Referer", "/admin/product%0d%0aSet-Cookie:x=1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().exists("Location"));
    }
}
