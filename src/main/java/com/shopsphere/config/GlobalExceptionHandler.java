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
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleBusiness(IllegalArgumentException e,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/");
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
