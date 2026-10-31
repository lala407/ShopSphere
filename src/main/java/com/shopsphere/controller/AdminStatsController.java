// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AdminStatsController 骨架（类声明、注解、字段 + 方法 TODO 步骤）
// 人工修改：stats 方法体由 AI 按学员确认补充实现（学员授权直接补全）
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.controller;

import com.shopsphere.common.OrderStatus;
import com.shopsphere.repository.OrderItemRepository;
import com.shopsphere.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 后台·统计报表（计划书 3.6）：订单总数 / 已付款订单数 / 总销售额 / 热销 Top5。
 *
 * <p>[AI骨架] 统计 SQL 已由 AI 在 Repository 里实现完毕（计划书分工：统计 SQL 属 AI）。
 * 你要做的只是"调方法 + 塞 model"——但请先读一遍那两个查询方法的 javadoc，
 * 答辩会问 GROUP BY 和接口投影。
 */
@Controller
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * "付过款"的状态集合：已付款 / 已发货 / 已完成。
     * 待付款（没成交）和已取消（退款了）都不算业绩。
     * 统计页的三个指标共用这一个口径——常量抽出来，保证口径统一。
     */
    private static final List<OrderStatus> PAID_STATUSES =
            List.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.COMPLETED);

    /**
     * 统计页 GET /admin/stats。
     *
     * model 需要四样（key 一字不差）：
     *   orderCount  —— 订单总数。提示：orderRepository.count()，JpaRepository 自带，不用写
     *   paidCount   —— 已付款订单数。提示：countByStatusIn(PAID_STATUSES)
     *   totalSales  —— 总销售额。提示：sumTotalAmountByStatusIn(PAID_STATUSES)
     *   hotProducts —— 热销 Top5。提示：findHotProducts(PAID_STATUSES, PageRequest.of(0, 5))
     */
    @GetMapping
    public String stats(Model model) {
        model.addAttribute("orderCount", orderRepository.count());
        model.addAttribute("paidCount", orderRepository.countByStatusIn(PAID_STATUSES));
        model.addAttribute("totalSales", orderRepository.sumTotalAmountByStatusIn(PAID_STATUSES));
        // PageRequest.of(0, 5) 等价 LIMIT 5 → Top5
        model.addAttribute("hotProducts",
                orderItemRepository.findHotProducts(PAID_STATUSES, PageRequest.of(0, 5)));
        return "admin/stats";
    }
}
