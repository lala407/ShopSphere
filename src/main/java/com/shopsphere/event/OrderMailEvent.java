// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：OrderMailEvent 邮件事件（Day8 邮件通知）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.event;

import com.shopsphere.common.MailType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 邮件事件：业务层"说要发信"，监听器"真的去发"。
 *
 * <p>【为什么要绕一层事件，不在 OrderService 里直接调 MailService？】
 * <ol>
 *   <li><b>事务时机</b>：下单方法带 @Transactional，如果方法体里直接发信，
 *       万一后面（比如库存扣减）抛异常回滚，邮件却已经发出去了——
 *       顾客收到"下单成功"，库里却没有这张订单。事件 + AFTER_COMMIT 保证"先提交，再发信"。</li>
 *   <li><b>解耦</b>：OrderService 不需要知道 SMTP 存在。
 *       以后要加短信/站内信，只需要再写一个监听器，订单代码一行不改。</li>
 *   <li><b>故障隔离</b>：发信失败不能影响下单，监听器内部 catch 掉即可。</li>
 * </ol>
 *
 * <p>为什么用 record 而不是普通类？
 * 事件是"不可变的消息载体"，创建后不该被修改。record 一行拿到构造器/getter/toString，
 * 正好符合这个语义（Java 16+ 特性，本项目用 21）。
 *
 * <p>注意：这里只带<b>值</b>（邮箱、单号、金额），不带 Order 实体。
 * 因为 AFTER_COMMIT 阶段事务已结束，Hibernate 的持久化上下文已关闭，
 * 传实体进去很容易踩 LazyInitializationException；传值最安全。
 */
public record OrderMailEvent(

        /** 订单 id（邮件里"查看订单"链接用）。 */
        Long orderId,

        /** 订单号（邮件标题和正文都要显示）。 */
        String orderNo,

        /** 收件人邮箱，可能为 null（老数据/未填邮箱）。 */
        String toEmail,

        /** 收货人姓名，正文称呼用。 */
        String receiverName,

        /** 订单金额。 */
        BigDecimal totalAmount,

        /** 商品明细摘要，如"华为 Mate 60 Pro ×2；三体（全集三册） ×1"。 */
        String itemSummary,

        /** 邮件类型：决定标题、正文口吻和要点。 */
        MailType type,

        /** 事件发生时间（下单/付款/发货时刻）。 */
        LocalDateTime happenedAt
) {
}
