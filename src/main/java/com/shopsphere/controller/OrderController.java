// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：OrderController 骨架（类声明、注解、字段 + 各方法 TODO 步骤）
// 人工修改：全部方法体由本人手写
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.controller;

import com.shopsphere.entity.Order;
import com.shopsphere.entity.OrderItem;
import com.shopsphere.repository.MailLogRepository;
import com.shopsphere.service.CartService;
import com.shopsphere.service.OrderService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 结算 + 我的订单。
 *
 * <p>[AI骨架] 类注解与依赖已就位，方法体由你手写。
 *
 * <p>路径规划：
 * <pre>
 * GET  /checkout          结算页（展示购物车 + 收货表单）
 * POST /order/create      提交订单（成功后重定向到订单详情）
 * GET  /orders            我的订单列表
 * GET  /orders/{id}       订单详情
 * POST /orders/{id}/pay   模拟付款
 * </pre>
 * 这些路径都已在 LoginInterceptor 的拦截范围内（/checkout 与 /order/** 与 /orders/**）。
 */
@Controller
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CartService cartService;

    /**
     * [AI辅助 Day8] 邮件发送记录（订单详情页显示"通知邮件已发送至 xxx"）。
     * 纯查询，按项目惯例直接调 Repository，不隔一层 Service。
     */
    private final MailLogRepository mailLogRepository;

    /**
     * 结算页 GET /checkout。
     *
     * model 需要三样：
     *   items       —— 购物车项（让用户确认买什么）
     *   totalAmount —— 合计（与购物车页同一口径）
     *   （cartCount 由 CartCountAdvice 全局注入，不用管）
     */
    @GetMapping("/checkout")
    public String checkout(HttpSession session, Model model) {
        // [手写] TODO：
       Long userId = (Long) session.getAttribute("userId");
        model.addAttribute("items", cartService.getItems(userId));
        model.addAttribute("totalAmount", cartService.getTotalAmount(userId));
        return "checkout";

    }

    /**
     * 提交订单 POST /order/create。
     *
     * 成功 → 重定向到 /orders/{新订单id}（PRG，避免刷新重复下单）
     * 失败 → flash error + 重定向回 /checkout（让用户改了再提交）
     */
    @PostMapping("/order/create")
    public String create(@RequestParam String receiverName,
                         @RequestParam String receiverPhone,
                         @RequestParam String receiverAddress,
                         @RequestParam(required = false) String remark,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        // [手写] TODO：
        Long userId = (Long) session.getAttribute("userId");
        try {
              Order order = orderService.createOrder(userId, receiverName, receiverPhone, receiverAddress, remark);
              return "redirect:/orders/" + order.getId();
           } catch (IllegalArgumentException e) {
          redirectAttributes.addFlashAttribute("error", e.getMessage());
              return "redirect:/checkout";
         }

    }

    /** 我的订单列表 GET /orders。 */
    @GetMapping("/orders")
    public String list(HttpSession session, Model model) {
        // [手写] TODO：
         Long userId = (Long) session.getAttribute("userId");
         model.addAttribute("orders", orderService.listByUser(userId));
         return "orders";

    }

    /**
     * 订单详情 GET /orders/{id}。
     *
     * ⚠️ 必须走 orderService.getOwnedOrder(userId, id)（内部按 user_id 过滤），
     * 不能直接 findById——否则改个 id 就能看别人订单（越权）。
     */
    @GetMapping("/orders/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model) {
        // [手写] TODO：
        Long userId = (Long) session.getAttribute("userId");
        try {
             Order order = orderService.getOwnedOrder(userId, id);
              List<OrderItem> items = orderService.getItems(id);
               model.addAttribute("order", order);
              model.addAttribute("items", items);
              // [AI辅助 Day8] 通知邮件记录：页面据此显示"已发送/未发送 + 邮箱 + 时间"
              model.addAttribute("mailLogs", mailLogRepository.findByOrderIdOrderByIdAsc(id));
               return "order-detail";
           } catch (IllegalArgumentException e) {
               return "redirect:/orders";     // 订单不存在/不是自己的 → 回列表
           }

    }

    /** 模拟付款 POST /orders/{id}/pay。 */
    @PostMapping("/orders/{id}/pay")
    public String pay(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        // [手写] TODO：
          Long userId = (Long) session.getAttribute("userId");
          try {
             orderService.pay(userId, id);
          } catch (IllegalArgumentException e) {
              redirectAttributes.addFlashAttribute("error", e.getMessage());
          }
           return "redirect:/orders/" + id;

    }

    /**
     * [AI辅助 P1-3] 用户取消自己的待付款订单 POST /orders/{id}/cancel。
     * Service 侧校验：订单归属 + 只有 CREATED 可自行取消（已付款走后台）。
     */
    @PostMapping("/orders/{id}/cancel")
    public String cancel(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Long userId = (Long) session.getAttribute("userId");
        try {
            orderService.cancelByUser(userId, id);
            redirectAttributes.addFlashAttribute("msg", "订单已取消，库存已回补");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    /**
     * [AI辅助 P1-3] 用户确认收货 POST /orders/{id}/confirm（SHIPPED → COMPLETED）。
     * 这是状态机里 COMPLETED 的唯一入口（P1-3 修复前该状态无任何流转可达）。
     */
    @PostMapping("/orders/{id}/confirm")
    public String confirm(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Long userId = (Long) session.getAttribute("userId");
        try {
            orderService.confirmByUser(userId, id);
            redirectAttributes.addFlashAttribute("msg", "已确认收货，感谢购买！");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/orders/" + id;
    }
}
