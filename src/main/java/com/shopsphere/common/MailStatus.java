// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：MailStatus 邮件发送结果枚举（Day8 邮件通知）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.common;

/**
 * 邮件发送结果。
 *
 * <p>为什么区分 SKIPPED 和 FAILED？
 * <ul>
 *   <li><b>SENT</b>：真的发出去了（SMTP 返回成功）；</li>
 *   <li><b>SKIPPED</b>：压根没发（用户没填邮箱 / 服务端没配 SMTP 账号）——
 *       这是"预期内"的情况，不算故障，页面提示也不同；</li>
 *   <li><b>FAILED</b>：发了但失败了（授权码错、网络超时、被判定垃圾邮件）——
 *       这才是需要排查的异常。</li>
 * </ul>
 *
 * <p>三种状态都会落 mail_log，订单详情页直接照着显示，
 * 老师不登录邮箱也能看到"邮件到底发没发"的证据。
 */
public enum MailStatus {

    SENT("已发送"),
    FAILED("发送失败"),
    SKIPPED("未发送");

    private final String label;

    MailStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
