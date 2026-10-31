// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AdminInterceptor —— /admin/** 仅 ADMIN 角色可进（计划书 3.6）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.interceptor;

import com.shopsphere.common.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员拦截器：/admin/** 只允许 ADMIN 角色访问。
 *
 * <p>三道判断（顺序有讲究）：
 * <ol>
 *   <li>没登录 → 302 去登录页（先解决"你是谁"）</li>
 *   <li>登录了但不是 ADMIN → 403 Forbidden（再解决"你有没有权限"）</li>
 *   <li>是 ADMIN → 放行</li>
 * </ol>
 *
 * <p>401 vs 403（面试常问）：401 = 未认证（没登录），403 = 已认证但没权限。
 * 本拦截器把 401 的场景转成"跳登录页"（对浏览器用户更友好），
 * 403 则交给 templates/error/403.html 渲染友好页面。
 */
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        HttpSession session = request.getSession();

        // 1) 未登录：跳登录页（和 LoginInterceptor 同一策略）
        if (session.getAttribute("userId") == null) {
            response.sendRedirect("/login");
            return false;
        }

        // 2) 已登录但不是管理员：403，由 error/403.html 渲染
        //    用 UserRole.ADMIN.equals(role) 而不是 role.equals(...) —— role 为 null 时不 NPE
        Object role = session.getAttribute("role");
        if (!UserRole.ADMIN.equals(role)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);   // 403
            return false;
        }

        // 3) 管理员：放行
        return true;
    }
}
