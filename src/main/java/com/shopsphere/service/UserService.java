// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：UserService 骨架（类声明 + 方法签名，方法体为 TODO）
// 人工修改：字段、注解、方法体由本人手写
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.service;

import com.shopsphere.common.UserRole;
import com.shopsphere.entity.User;
import com.shopsphere.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 用户业务层：注册、登录。
 *
 * <p>为什么 Controller 不直接用 Repository，中间隔一层 Service？
 * → 业务规则（查重、加密、禁用判断）放 Service，Controller 只管
 * HTTP 进出。换掉页面/加接口时业务代码一行不用动（可复用）。
 *
 * <p>[AI骨架] 类声明与方法签名已就位，注解、字段、方法体由本人手写。
 *
 * <p>计划书 6.4 决策：MVP 阶段 Service 不写 interface + impl，
 * 单一实现不需要为多态买单。
 */
// ==========================================================
// [手写] TODO 1：类上加两个注解——
    @Service
    @RequiredArgsConstructor

public class UserService {
    // [手写] TODO 2：声明两个 private final 字段（依赖注入）——
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    public void register(String username, String password, String email) {

        // 1) 校验非空 / 长度
        if (username == null || username.isBlank()
                || username.length() < 3 || username.length() > 20) {
            throw new IllegalArgumentException("用户名长度需在 3-20 字符之间");
        }
        if (password == null || password.isBlank()
                || password.length() < 6 || password.length() > 20) {
            throw new IllegalArgumentException("密码长度需在 6-20 字符之间");
        }

        // 2) [AI辅助 Day8] 邮箱必填 + 格式校验
        //    前端 type="email" 只是"提示"，改个请求就能绕过，后端必须再校验一遍。
        //    正则只做基本格式判断（有本地部分、@、域名、至少两段后缀），
        //    真正的有效性只能靠发一封邮件验证（本实验不做邮箱验证码，记录为已知限制）。
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空（用于接收订单确认与发货通知）");
        }
        email = email.trim();
        if (email.length() > 100 || !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("邮箱格式不正确，例如 name@example.com");
        }

        // 3) 用户名查重
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("用户名已被占用");
        }

        // 4) 组装实体
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(1);

        // 5) 密码加密后再入库
        user.setPassword(passwordEncoder.encode(password));

        // 6) 保存
        userRepository.save(user);
    }

    /**
     * 登录。
     * 约定：成功返回 User（由 Controller 写进 Session）；
     * 失败统一抛 "用户名或密码错误"，防止攻击者探测用户名是否存在。
     */
    public User login(String username, String password) {

        // 1) 查用户
        Optional<User> opt = userRepository.findByUsername(username);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        User user = opt.get();

        // 2) 校验密码（先明文，后库里的哈希）
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 3) 检查账号状态
        if (user.getStatus() != 1) {
            throw new IllegalArgumentException("账号已被禁用");
        }

        // 4) 返回
        return user;
    }
}