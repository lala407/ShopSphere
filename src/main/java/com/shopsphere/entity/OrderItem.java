// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：OrderItem 订单明细实体（价格快照，计划书 5.2）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细 order_item —— 价格快照表。
 *
 * <p>核心思想：下单那一刻把商品名和单价"拍照"存下来。
 * 之后商家改价、改名、甚至下架商品，这张订单的金额和名称都不变。
 *
 * <p>为什么 product_id 用裸 Long 而不是 @ManyToOne Product？
 * 订单是历史凭证，不应该依赖商品表的当前状态（商品可能被物理删除）。
 * 这里只留 id 做追溯，不做强外键约束（计划书 5.1 明确要求）。
 *
 * <p>为什么还要单独存 subtotal（单价×数量）？
 * 便于统计 SQL 直接 SUM(subtotal)，不用每次乘算；
 * 也避免了"展示时算错"的风险（展示逻辑和统计口径统一）。
 */
@Entity
@Table(
        name = "order_item",
        indexes = @Index(name = "idx_order_item_order", columnList = "order_id")
)
@Data
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属订单。
     * 单向多对一：明细知道自己属于哪张订单，订单不持有明细 List。
     * 查明细用 OrderItemRepository.findByOrderId，比维护双向集合简单可靠。
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** 商品 id：仅留痕追溯，不做外键。 */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 快照：下单时的商品名。 */
    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    /** 快照：下单时的单价。 */
    @Column(name = "product_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal productPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** 单价 × 数量。 */
    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
}
