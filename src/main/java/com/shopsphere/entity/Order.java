// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：Order 订单实体（对应表 orders，计划书 5.2）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.entity;

import com.shopsphere.common.OrderStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体 orders。
 *
 * <p>表名为什么是 orders 而不是 order？
 * order 是 SQL 保留字（ORDER BY），MySQL 下虽然可以用反引号绕开，
 * 但任何手写 SQL、工具面板都容易踩坑，加 s 最省心。
 *
 * <p>为什么 total_amount 要落库（和购物车的"计算值不落库"相反）？
 * 购物车是"临时清单"，价格应该跟着商品实时变；
 * 订单是"已成交的合同"，金额必须固化在下单那一刻，之后商品涨价也不能变。
 * 两类数据性质不同，所以策略相反——这是答辩可以讲的设计取舍。
 */
@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_no", columnNames = {"order_no"}),
        indexes = {
                // "我的订单"按时间倒序
                @Index(name = "idx_order_user_created", columnList = "user_id, created_at"),
                // 后台按状态筛选
                @Index(name = "idx_order_status_created", columnList = "status, created_at")
        }
)
@Data
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 业务单号（展示给用户看的），全局唯一。
     * 为什么不直接给用户看自增 id？自增 id 会暴露订单量、可被枚举遍历。
     */
    @Column(name = "order_no", nullable = false, length = 32)
    private String orderNo;

    /** 下单人。只保留单向多对一，不建 User 里的 List<Order>，避免双向维护。 */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 下单时固化的总金额（DECIMAL(10,2)）。 */
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /**
     * 状态：@Enumerated(EnumType.STRING) 存名字符串（CREATED），
     * 不要用默认的 ORDINAL——枚举顺序一变，历史数据全错位。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(name = "receiver_name", nullable = false, length = 50)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(name = "receiver_address", nullable = false, length = 255)
    private String receiverAddress;

    @Column(name = "remark", length = 255)
    private String remark;

    /** 关键节点时间：只有走到对应状态才有值，所以可空。 */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    /** [AI辅助 P1-3] 确认收货时间（SHIPPED → COMPLETED 时写入）。 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
