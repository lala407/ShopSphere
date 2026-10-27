// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：LoginInterceptor —— 未登录访问受保护路径时跳转登录页（计划书 3.2 AI 范围）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：检查 Session 里有没有 userId。
 *
 * <p>HandlerInterceptor 的三个时机（本类只用 preHandle）：
 * <ul>
 *   <li>preHandle    —— Controller 方法执行【前】。返回 false = 拦下，不再进 Controller</li>
 *   <li>postHandle   —— Controller 执行后、视图渲染前（很少用）</li>
 *   <li>afterCompletion —— 视图渲染完成后（一般做清理/审计）</li>
 * </ul>
 *
 * <p>和 Filter 的区别（面试常问）：
 * Interceptor 由 Spring MVC 管理，能拿到 Handler 信息、能注入 Bean；
 * Filter 由 Servlet 容器管理，更底层、范围更大。
 */
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 放行逻辑：Session 里有 userId = 已登录，放行；否则跳转登录页。
     *
     * <p>session.getAttribute 返回 Object（取出来可能是任何类型），
     * 所以用 Object 接——只需要判断非空，不用关心具体类型。
     *
     * <p>sendRedirect 返回 302 + Location 响应头，
     * 浏览器收到后会自动再发一次 GET /login。
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        Object userId = request.getSession().getAttribute("userId");
        if (userId != null) {
            return true;    // 已登录，放行
        }
        // 未登录：302 跳转到登录页，返回 false 阻止继续执行 Controller
        response.sendRedirect("/login");
        return false;
    }
}
