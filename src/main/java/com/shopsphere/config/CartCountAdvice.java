// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：CartCountAdvice —— 全局注入购物车角标数量 cartCount（Day 4 AI 基础设施）
// 人工修改：无（请读懂）
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.config;

import com.shopsphere.service.CartService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 全局模型属性：让每个页面都能拿到 ${cartCount}。
 *
 * <p>为什么需要 @ControllerAdvice？
 * 导航栏是公共片段（nav.html），出现在所有页面。如果每个 Controller 都
 * model.addAttribute("cartCount", ...)，会重复几十次。
 * @ControllerAdvice + @ModelAttribute 的方法会在**每个 Controller 方法执行前**
 * 自动调用，把返回值放进 Model——一处定义，全站可用。
 *
 * <p>和拦截器的区别：
 * 拦截器管"能不能进"（权限），ControllerAdvice 管"塞什么数据"（视图模型）。
 *
 * <p>性能取舍：每个页面请求多一次购物车查询。本项目购物车项很少，
 * 可接受；若要优化，可改成 Session 里存计数（但就有一致性风险）。
 */
@ControllerAdvice
public class CartCountAdvice {

    private final CartService cartService;

    public CartCountAdvice(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * 未登录 → 0（不查库）；已登录 → 查件数。
     *
     * <p>参数 HttpSession 由 Spring 自动注入；
     * getSession() 若不存在会创建，不会返回 null。
     */
    @ModelAttribute("cartCount")
    public long cartCount(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            return 0L;
        }
        return cartService.getCartCount((Long) userId);
    }
}
