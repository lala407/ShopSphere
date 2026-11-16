// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：CsrfTokenFilter —— 轻量 CSRF 防护（P2-5，不引入 Spring Security）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.UUID;

/**
 * 轻量 CSRF 防护（不引 Spring Security 的实现）。
 *
 * <p><b>为什么需要</b>：浏览器发请求会自动带上本站 Cookie，攻击者做一个恶意页面
 * 偷偷 POST 到 {@code /admin/product/1/delete} 或 {@code /orders/5/pay}，
 * 已登录用户的会话会被借用，这就是 CSRF。防御的标准做法是「请求必须携带只有本站页面才知道的随机串」。
 *
 * <p><b>做法</b>（同步令牌模式）：
 * <ol>
 *   <li>每个会话生成一个随机 token 存在 Session 里；</li>
 *   <li>页面渲染时把它写进 {@code <meta name="_csrf">}，
 *       页脚脚本在提交前给所有 POST 表单动态补一个隐藏域 {@code _csrf}；</li>
 *   <li>本过滤器对 POST/PUT/PATCH/DELETE 校验参数或 {@code X-CSRF-Token} 头，
 *       不匹配直接 403——攻击者的页面读不到本站 DOM，也就拿不到这个 token。</li>
 * </ol>
 *
 * <p><b>为什么不用 Spring Security</b>：它会给全站套一层默认登录墙与过滤器链，
 * 与本项目「手写拦截器 + Session」的教学结构冲突；课程作业只需覆盖 CSRF 这一点，
 * 60 行过滤器比引入整套框架更透明（也便于在报告里讲清楚原理）。
 *
 * <p><b>开关</b>：{@code shopsphere.security.csrf.enabled}，测试 profile 置 false，
 * 免得每个用例都要先取 token；单独的 {@code CsrfProtectionTest} 再把它打开验证。
 */
@Component
public class CsrfTokenFilter extends OncePerRequestFilter {

    /** Session 里存 token 的键。 */
    public static final String SESSION_KEY = "csrfToken";
    /** 表单隐藏域 / 请求参数名。 */
    public static final String PARAM_NAME = "_csrf";
    /** 请求头名（AJAX 用；本项目表单为主，保留以便扩展）。 */
    public static final String HEADER_NAME = "X-CSRF-Token";
    /** 会改变服务器状态的请求方法。 */
    private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Value("${shopsphere.security.csrf.enabled:true}")
    private boolean enabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 静态资源不建会话、不校验：给每个图片请求发一个 JSESSIONID 纯属浪费
        String uri = request.getRequestURI();
        if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/")
                || uri.equals("/favicon.ico") || uri.equals("/healthz") || uri.startsWith("/error")) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(true);
        String token = (String) session.getAttribute(SESSION_KEY);
        if (token == null) {
            token = UUID.randomUUID().toString();
            session.setAttribute(SESSION_KEY, token);
        }
        // 供 Thymeleaf 在 <meta> 里取用（模板里写 ${_csrf}）
        request.setAttribute(PARAM_NAME, token);

        if (enabled && UNSAFE_METHODS.contains(request.getMethod())) {
            String submitted = request.getParameter(PARAM_NAME);
            if (submitted == null || submitted.isBlank()) {
                submitted = request.getHeader(HEADER_NAME);
            }
            if (!constantTimeEquals(token, submitted)) {
                // sendError 会走容器的错误页机制，落到 templates/error/403.html
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF token 校验失败");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /** 定长比较，避免按字符短路泄露前缀（令牌校验的标准写法）。 */
    private boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /** 只在测试里用：确认开关默认值。 */
    boolean isEnabled() {
        return enabled;
    }
}
