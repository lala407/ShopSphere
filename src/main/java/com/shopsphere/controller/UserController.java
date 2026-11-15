// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：UserController 骨架（类声明 + 6 个方法签名，方法体为 TODO）
// 人工修改：注解、字段、方法体由本人手写
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.controller;

import com.shopsphere.entity.User;
import com.shopsphere.service.UserService;
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
     * ⚠️ 骨架原来没有 Model 参数，但 catch 里要 addAttribute，
     *    所以这里必须补一个 Model 参数（见下面解释）。
     */
    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpSession session,
                          Model model) {
        try {
            User user = userService.login(username, password);
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            session.setAttribute("role", user.getRole());
            return "redirect:/";
        } catch (IllegalArgumentException e) {
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