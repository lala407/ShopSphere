// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：CartController 骨架（类声明、注解、字段 + 各方法 TODO 步骤）
// 人工修改：全部方法体由本人手写
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.controller;

import com.shopsphere.service.CartService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * 购物车页 GET /cart。
     */
    @GetMapping
    public String list(HttpSession session, Model model) {

        // [手写] 从 Session 取 userId（拦截器已保证 /cart/** 必须登录）
        Long userId = (Long) session.getAttribute("userId");

        // [手写] 购物车项列表 + 合计金额
        model.addAttribute("items", cartService.getItems(userId));
        model.addAttribute("totalAmount", cartService.getTotalAmount(userId));

        return "cart";
    }

    /**
     * 加入购物车 POST /cart/add。
     */
    @PostMapping("/add")
    public String add(@RequestParam Long productId,
                      @RequestParam(defaultValue = "1") int quantity,
                      HttpSession session,
                      RedirectAttributes redirectAttributes) {


        // [手写] 从 Session 取 userId
        Long userId = (Long) session.getAttribute("userId");

        try {
            cartService.addToCart(userId, productId, quantity);
            return "redirect:/cart";
        } catch (IllegalArgumentException e) {
            // [手写] 失败：flash 暂存错误，退回商品详情页就近提示
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/product/" + productId;
        }
    }

    /** 修改数量 POST /cart/update（quantity <= 0 时 Service 内部按删除处理）。 */
    @PostMapping("/update")
    public String update(@RequestParam Long cartItemId,
                         @RequestParam int quantity,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
         // [手写] 从 Session 取 userId
        Long userId = (Long) session.getAttribute("userId");

        try {
            cartService.updateQuantity(userId, cartItemId, quantity);
        } catch (IllegalArgumentException e) {
            // [手写] 失败也回购物车页，flash 带上错误信息
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cart";
    }

    /** 删除单项 POST /cart/remove。 */
    @PostMapping("/remove")
    public String remove(@RequestParam Long cartItemId,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {

        // [手写] 从 Session 取 userId
        Long userId = (Long) session.getAttribute("userId");

        try {
            cartService.removeItem(userId, cartItemId);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cart";
    }

    /** 清空购物车 POST /cart/clear。 */
    @PostMapping("/clear")
    public String clear(HttpSession session) {

        // [手写] 从 Session 取 userId，直接清空
        Long userId = (Long) session.getAttribute("userId");
        cartService.clear(userId);
        return "redirect:/cart";
    }
}