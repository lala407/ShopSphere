// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AdminOrderController 骨架（类声明、注解、字段 + 各方法 TODO 步骤）
// 人工修改：全部方法体由 AI 按学员确认补充实现（学员授权直接补全）
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.controller;

import com.shopsphere.common.OrderStatus;
import com.shopsphere.entity.Order;
import com.shopsphere.repository.MailLogRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 后台·订单管理（计划书 3.6）：列表（按状态筛选）/ 详情 / 发货 / 取消。
 *
 * <p>[AI骨架] 路由与方法签名已就位，方法体由你手写。
 * 发货 ship() / 取消 cancel() 的事务与库存回补逻辑已由 AI 在 OrderService 实现
 * （订单状态流转属 AI 范围），Controller 只负责"接请求 → 调服务 → 重定向"。
 *
 * <p>纯查询（列表/详情）直接调 OrderRepository——和前台 ProductController 同一原则。
 */
@Controller
@RequestMapping("/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /** [AI辅助 Day8] 邮件发送记录：后台详情页也能看到"发货通知发没发出去"。 */
    private final MailLogRepository mailLogRepository;

    /**
     * 订单列表 GET /admin/order?status=
     *
     * @param status 状态筛选，null = 全部。
     *               Spring 会自动把 ?status=PAID 这样的字符串转成枚举，不用手写 valueOf。
     *
     * model 需要三样：
     *   orders   —— List&lt;Order&gt;（status 为 null 查全部、否则按状态查；都按下单时间倒序，
     *               两个查询方法由你在 OrderRepository 的 TODO 3 / TODO 4 手写）
     *   statuses —— OrderStatus.values()（页面渲染筛选下拉框用）
     *   status   —— 当前选中的状态（页面回显选中项；没选就是 null）
     */
    @GetMapping
    public String list(@RequestParam(required = false) OrderStatus status, Model model) {
        // status 为 null 查全部，否则按状态查；都按下单时间倒序
        List<Order> orders = (status == null)
                ? orderRepository.findAllByOrderByCreatedAtDesc()
                : orderRepository.findByStatusOrderByCreatedAtDesc(status);
        model.addAttribute("orders", orders);
        model.addAttribute("statuses", OrderStatus.values());   // 筛选下拉框
        model.addAttribute("status", status);                    // 回显选中项
        return "admin/order-list";
    }

    /**
     * 订单详情 GET /admin/order/{id}。
     *
     * model 需要：order + items（明细用 orderService.getItems(id)，Day5 已有）。
     *
     * 注意：后台详情【不校验归属】——管理员可以看任何人的订单，
     * 所以用 orderRepository.findById，而不是前台的 getOwnedOrder。
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        // 后台不校验归属（管理员可看任何人订单）；不存在交给全局异常处理
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        model.addAttribute("order", order);
        model.addAttribute("items", orderService.getItems(id));
        model.addAttribute("mailLogs", mailLogRepository.findByOrderIdOrderByIdAsc(id));
        return "admin/order-detail";
    }

    /**
     * 发货 POST /admin/order/{id}/ship（PAID → SHIPPED）。
     *
     * 无论成功失败都重定向回详情页：成功 flash msg，失败（状态不对）flash error。
     */
    @PostMapping("/{id}/ship")
    public String ship(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            orderService.ship(id);
            redirectAttributes.addFlashAttribute("msg", "已发货");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/order/" + id;
    }

    /**
     * 取消订单 POST /admin/order/{id}/cancel（CREATED/PAID → CANCELLED，自动回补库存）。
     */
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            orderService.cancel(id);
            redirectAttributes.addFlashAttribute("msg", "已取消并回补库存");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/order/" + id;
    }
}
