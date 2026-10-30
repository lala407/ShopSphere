// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：OrderStatus 订单状态枚举（计划书 5.3 订单状态机）
// 人工修改：无（请读懂状态流转规则）
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.common;

/**
 * 订单状态机（计划书 5.3）。
 *
 * <pre>
 * CREATED ──付款──> PAID ──发货──> SHIPPED ──收货──> COMPLETED
 *    │               │
 *    └──取消──> CANCELLED <──取消(回补库存)──┘
 * </pre>
 *
 * <p>规则：
 * <ol>
 *   <li>只有 CREATED、PAID 可取消；SHIPPED 之后不可取消。</li>
 *   <li>取消 PAID 订单要回补库存。</li>
 *   <li>前台用户只能操作自己的订单（按 user_id 过滤）。</li>
 * </ol>
 *
 * <p>为什么带中文 label？
 * 数据库存枚举名（CREATED），页面要显示"待付款"。
 * 把展示文案放在枚举里，避免页面写一堆 th:if 判断——
 * 这是"枚举承载业务语义"的常见做法。
 */
public enum OrderStatus {

    CREATED("待付款"),
    PAID("已付款"),
    SHIPPED("已发货"),
    COMPLETED("已完成"),
    CANCELLED("已取消");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
