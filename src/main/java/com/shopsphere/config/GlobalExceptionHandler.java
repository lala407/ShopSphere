// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：GlobalExceptionHandler —— 全局异常处理（计划书 3.6：统计 SQL 与全局异常属 AI 范围）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器（计划书 3.6：业务异常 → 提示；其他异常 → 500 页 + 日志）。
 *
 * <p>【@ControllerAdvice 是什么】
 * 它是 AOP 在 MVC 层的应用：给【所有】Controller 统一套一层"异常兜底"。
 * 任何 Controller 方法抛出、且没被 try-catch 接住的异常，
 * 都会按异常类型路由到这里对应的 @ExceptionHandler 方法。
 * Controller 里那些 try-catch 不变——它们优先级更高，这里是"漏网之鱼"的终点。
 *
 * <p>【为什么要分流两类异常】
 * <ul>
 *   <li><b>业务异常</b>（IllegalArgumentException，如"库存不足""状态不允许发货"）：
 *       是用户的操作问题 → flash 提示 + 退回上一页，让用户改正。</li>
 *   <li><b>系统异常</b>（NPE、SQL 异常等）：是我们的代码问题 →
 *       用户看不懂也改不了，给 500 页；同时 log.error 打完整堆栈供排查。</li>
 * </ul>
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：flash 提示 + 重定向回来源页。
     *
     * <p>Referer 请求头记录"用户从哪个页面发来的请求"，
     * 出错了就把他送回去（带着错误提示），体验比停在错误页好。
     * 极少数情况没有 Referer（直接输 URL），退回首页兜底。
     *
     * <p>[P2-6] <b>Referer 是客户端可控的</b>，直接拼进 {@code redirect:} 就是开放重定向：
     * 攻击者可构造 {@code Referer: https://evil.example/login}，让本站域名跳去钓鱼页。
     * 这里只接受"以单个 / 开头的站内绝对路径"：
     * <ul>
     *   <li>拒绝 {@code //evil.com}（协议相对 URL，浏览器会当成外站）；</li>
     *   <li>带协议的 URL 只保留 :// 之后的 path 部分，<b>主机名一律丢弃</b>——
     *       所以 {@code https://evil.com/login} 会变成本站的 {@code /login}，跳不出站；</li>
     *   <li>拒绝含换行/回车的值（响应头注入）；</li>
     *   <li>其余情况回退首页，宁可少一点体验也不能被跳去外站。</li>
     * </ul>
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleBusiness(IllegalArgumentException e,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        return "redirect:" + safeRedirectPath(request.getHeader("Referer"));
    }

    /**
     * 把 Referer 收敛成安全的站内路径（P2-6）。
     *
     * <p>只保留 path + query，丢掉协议与主机——这样即使 Referer 是本站完整 URL
     * （如 {@code http://localhost:8080/cart}）也能正确回去，而外站 URL 一律回首页。
     */
    private String safeRedirectPath(String referer) {
        if (referer == null || referer.isBlank()) {
            return "/";
        }
        // 换行/回车会截断响应头，直接拒绝
        if (referer.indexOf('\n') >= 0 || referer.indexOf('\r') >= 0) {
            return "/";
        }
        String path = referer;
        int schemeEnd = path.indexOf("://");
        if (schemeEnd > 0) {
            // 有协议：取出 :// 之后的第一个 / 起的部分，并确认主机是本站
            int pathStart = path.indexOf('/', schemeEnd + 3);
            path = pathStart > 0 ? path.substring(pathStart) : "/";
        }
        // 协议相对（//evil.com）与非 / 开头的一律拒绝
        if (!path.startsWith("/") || path.startsWith("//")) {
            return "/";
        }
        return path;
    }

    /**
     * 系统异常：记日志 + 渲染 500 页。
     *
     * <p>⚠️ 关键的放行判断：
     * ResponseStatusException（Controller 主动抛的 404）和
     * NoResourceFoundException（访问了不存在的 URL，Spring Boot 3 的行为）
     * 都不是"系统故障"，必须原样抛回给 Spring，让它渲染 error/404.html。
     * 如果在这里吞掉，所有 404 都会变成 500——语义全错。
     */
    @ExceptionHandler(Exception.class)
    public String handleSystem(Exception e, Model model) throws Exception {
        if (e instanceof ResponseStatusException || e instanceof NoResourceFoundException) {
            throw e;    // 404 类异常不拦截，交给 Spring 渲染对应错误页
        }
        // log.error 的第二个参数传异常对象本身：日志里会有完整堆栈
        log.error("[系统异常] {} {}", e.getClass().getSimpleName(), e.getMessage(), e);
        model.addAttribute("errorMessage", e.getMessage());
        return "error/500";
    }
}
