// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：MailLogWriter —— 用独立事务写 mail_log（Day8 邮件通知）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.service;

import com.shopsphere.entity.MailLog;
import com.shopsphere.repository.MailLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邮件日志写入器：**必须用独立事务（REQUIRES_NEW）**。
 *
 * <p>【为什么单独抽一个类？这是一次真实踩坑换来的】
 * 邮件是在 {@code @TransactionalEventListener(AFTER_COMMIT)} 里发的，
 * 此时下单的那个事务<b>已经提交并结束</b>，但线程上仍然绑着上一个 EntityManager。
 * 这个时机直接调 {@code mailLogRepository.save(record)}：
 * <ul>
 *   <li>不报错（save 走的是 persist，只是把对象放进持久化上下文）；</li>
 *   <li>不落库（没有真正开启新事务，提交动作永远不会发生）；</li>
 *   <li>测试里表现就是"邮件发了，但日志一条都没有"——最阴的一类 bug。</li>
 * </ul>
 *
 * <p>把写入放进一个 {@code REQUIRES_NEW} 的方法里，Spring 会挂起外层（已结束的）事务上下文，
 * 真开一个新事务，写完立刻提交，日志才稳定落库。
 *
 * <p>为什么不直接把 {@code MailService.send} 整个标成 REQUIRES_NEW？
 * 那样 SMTP 的网络往返（最长 5 秒）会一直占着数据库连接。
 * 发信走网络、写日志走短事务，两者分开更干净。
 */
@Component
@RequiredArgsConstructor
public class MailLogWriter {

    private final MailLogRepository mailLogRepository;

    /**
     * 新事务写一条邮件日志。
     *
     * <p>这里**不吞异常**：吞掉会让上层误以为写成功了。
     * 真正的兜底在调用方 {@link MailService#send} 里（那里本来就要保证"发信失败不影响订单"）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(MailLog record) {
        mailLogRepository.save(record);
    }
}
