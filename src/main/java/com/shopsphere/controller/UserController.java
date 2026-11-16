// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：UserController 骨架（类声明 + 6 个方法签名）
// 人工修改：注解、字段、方法体由本人手写
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.controller;

import com.shopsphere.entity.User;
import com.shopsphere.service.LoginAttemptService;
import com.shopsphere.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final LoginAttemptService loginAttemptService;

    /** 显示注册页：GET /register → templates/register.html */
    @GetMapping("/register")
    public String showRegister() {
        return "register";
    }

    /**
     * 提交注册：POST /register
     *
     * <p>[AI辅助 Day8] email 保持 required = false，**校验放到 Service**：
     * 表单不填时 @RequestParam 直接抛 400（白页），体验差；
     * Service 里抛 IllegalArgumentException 才能显示"邮箱不能为空"的中文提示。
     * 这是"前端必填 + 后端兜底"两层校验的标准做法。
     */
    @PostMapping("/register")
    public String doRegister(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam(required = false) String email,
                             Model model) {
        try {
            userService.register(username, password, email);
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    /** 显示登录页：GET /login → templates/login.html */
    @GetMapping("/login")
    public String showLogin() {
        return "login";
    }

    /**
     * 提交登录：POST /login
     *
     * <p>[P2-5] 三处安全加固：
     * <ol>
     *   <li><b>失败限流</b>：同一用户名连错 5 次锁 5 分钟，挡住在线撞库；</li>
     *   <li><b>Session 轮换</b>：登录成功后 {@code session.invalidate()} 再新建一个会话，
     *       防 session fixation——攻击者若事先把某个 JSESSIONID 塞进受害者浏览器，
     *       受害者登录后沿用旧 ID 就等于把已登录会话送给攻击者；换 ID 后旧值作废；</li>
     *   <li>CSRF 由 {@code CsrfTokenFilter} 统一处理，本方法无需关心。</li>
     * </ol>
     *
     * <p>注意参数从 HttpSession 改成 HttpServletRequest：会话被作废后原对象不可再用，
     * 必须通过 request 重新取一个新会话。
     */
    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpServletRequest request,
                          Model model) {
        if (loginAttemptService.isBlocked(username)) {
            long seconds = loginAttemptService.remainingSeconds(username);
            model.addAttribute("error",
                    "登录失败次数过多，账号已临时锁定，请 " + Math.max(1, (seconds + 59) / 60) + " 分钟后再试");
            return "login";
        }
        try {
            User user = userService.login(username, password);
            loginAttemptService.onSuccess(username);

            // 防 session fixation：丢弃旧会话，换一个新的再写登录态
            HttpSession old = request.getSession(false);
            if (old != null) {
                old.invalidate();
            }
            HttpSession session = request.getSession(true);
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            session.setAttribute("role", user.getRole());
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            loginAttemptService.onFailure(username);
            model.addAttribute("error", e.getMessage());
            return "login";
        }
    }

    /** 注销：GET /logout → 销毁 Session，回登录页 */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}