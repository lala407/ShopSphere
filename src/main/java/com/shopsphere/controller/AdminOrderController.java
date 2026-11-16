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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    /** [AI辅助 P1-6] 后台列表每页条数，与商品管理保持一致（10 条/页）。 */
    private static final int PAGE_SIZE = 10;

    /**
     * 订单列表 GET /admin/order?status=&kw=&from=&to=&page=
     *
     * <p>[AI辅助 P1-6 修复] 原实现 findAll 全量加载（订单上几百条就卡），
     * 现改为 {@link OrderRepository#search} 的多条件分页查询：
     * <ul>
     *   <li>status —— 状态筛选，null = 全部。Spring 自动把 ?status=PAID 转成枚举；</li>
     *   <li>kw     —— 关键字，模糊匹配 订单号 / 收货人 / 下单用户名，null = 不过滤；</li>
     *   <li>from/to—— 下单日期区间（yyyy-MM-dd，由 {@code @DateTimeFormat} 解析），null = 不限；</li>
     *   <li>page   —— 页码，从 0 开始（URL 里是给人看的 1-based，模板里 +1 显示）。</li>
     * </ul>
     *
     * <p>model 需要：
     *   pageData —— Page&lt;Order&gt;（分页条读 totalPages / totalElements / first / last）
     *   statuses —— OrderStatus.values()（筛选下拉框）
     *   status / kw / from / to —— 回显，翻页链接要把这些条件带下去，否则一翻页筛选就丢了
     */
    @GetMapping
    public String list(@RequestParam(required = false) OrderStatus status,
                       @RequestParam(required = false) String kw,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {

        // 1) 空字符串当没填（表单没填时提交的是 kw=，不是 null）
        String keyword = (kw == null || kw.isBlank()) ? null : kw.trim();

        // 2) 日期 → 时间区间：左闭右开，to 当天 23:59:59 的订单也要能查到
        LocalDateTime fromTs = (from == null) ? null : from.atStartOfDay();
        LocalDateTime toTs = (to == null) ? null : to.plusDays(1).atStartOfDay();

        // 3) 分页：第 N 页、每页 10 条、下单时间倒序（同秒下单用 id 倒序兜底，保证顺序稳定）
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Order> pageData = orderRepository.search(status, keyword, fromTs, toTs, pageable);

        // 4) 塞 model（条件参数原样回传，模板里的翻页链接要用）
        model.addAttribute("pageData", pageData);
        model.addAttribute("statuses", OrderStatus.values());   // 筛选下拉框
        model.addAttribute("status", status);                    // 回显选中项
        model.addAttribute("kw", keyword);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
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

    /**
     * [AI辅助 P1-3] 确认收货（管理员代操作）POST /admin/order/{id}/confirm
     * （SHIPPED → COMPLETED，写 completed_at）。买家没点确认时后台可以代为收货。
     */
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            orderService.confirm(id);
            redirectAttributes.addFlashAttribute("msg", "已确认收货（订单完成）");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/order/" + id;
    }
}
