// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：MailType 邮件类型枚举（Day8 邮件通知）
// 人工修改：无（请读懂"枚举承载业务语义"的写法）
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.common;

/**
 * 邮件类型：一个订单在生命周期里会触发三封通知邮件。
 *
 * <pre>
 * 下单成功 CREATED ──> ORDER_CREATED（请付款）
 * 付款成功 PAID    ──> ORDER_PAID   （已收款，备货中）
 * 发货   SHIPPED   ──> ORDER_SHIPPED（已发出，注意查收）
 * </pre>
 *
 * <p>为什么用枚举而不是直接写字符串？
 * 邮件类型会写进 mail_log 表、页面标签、以及"同一订单同一类型只查最新一条"的判断，
 * 三处必须完全一致。枚举把拼写错误挡在编译期。
 */
public enum MailType {

    ORDER_CREATED("下单成功通知"),
    ORDER_PAID("付款成功通知"),
    ORDER_SHIPPED("发货通知");

    private final String label;

    MailType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
