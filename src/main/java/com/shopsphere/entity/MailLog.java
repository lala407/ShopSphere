// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：MailLog 实体（表 mail_log）—— 每次发信留痕（Day8 邮件通知）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.entity;

import com.shopsphere.common.MailStatus;
import com.shopsphere.common.MailType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮件发送日志 mail_log。
 *
 * <p>为什么发完邮件还要落库？
 * <ol>
 *   <li><b>可追溯</b>：SMTP 发出去就没了痕迹，出问题（顾客说没收到）时只能靠这张表；
 *   <li><b>可展示</b>：订单详情页要显示"确认邮件已发送至 xxx@xx（时间）"，
 *       这个"已发送"不能靠嘴说，得有个数据来源；
 *   <li><b>不阻塞主流程</b>：邮件失败只写 FAILED，订单照常成立——
 *       电商里"邮件是通知，不是交易的一部分"，绝不能因为 SMTP 挂了就回滚订单。
 * </ol>
 *
 * <p>表设计取舍：这里只存 order_id，不做 @ManyToOne 关联。
 * 邮件日志是"旁路审计数据"，不应该参与订单实体的加载；需要订单号时直接冗余一个 order_no 字段，
 * 查日志页时一条 SQL 就够，不用 join。
 */
@Entity
@Table(
        name = "mail_log",
        indexes = {
                // 订单详情页按订单查邮件记录
                @Index(name = "idx_mail_order", columnList = "order_id"),
                // 后台按时间倒序翻日志
                @Index(name = "idx_mail_created", columnList = "created_at")
        }
)
@Data
public class MailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联订单 id（不建外键关联，见类注释）。 */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 冗余订单号：看日志时不用回查 orders 表。 */
    @Column(name = "order_no", length = 32)
    private String orderNo;

    /** 收件人邮箱。用户可能没填，所以允许 NULL。 */
    @Column(name = "to_email", length = 100)
    private String toEmail;

    /** 邮件类型：ORDER_CREATED / ORDER_PAID / ORDER_SHIPPED。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MailType type;

    /** 发送结果：SENT / FAILED / SKIPPED。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MailStatus status;

    /** 失败原因（截断到 200 字，异常堆栈不要往这里塞）。 */
    @Column(name = "error_message", length = 200)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
