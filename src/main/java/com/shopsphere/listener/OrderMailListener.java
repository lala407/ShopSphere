// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：OrderMailListener —— 事务提交后再发信（Day8 邮件通知）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.listener;

import com.shopsphere.event.OrderMailEvent;
import com.shopsphere.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订单邮件监听器：把 OrderMailEvent 变成真实邮件。
 *
 * <p>【@TransactionalEventListener 是这个方案的核心】
 * <ul>
 *   <li>{@code phase = AFTER_COMMIT}：**事务提交成功之后**才执行。
 *       如果下单过程抛异常回滚，监听器根本不会被调用 → 不会出现"邮件发了、订单没了"。</li>
 *   <li>对比普通 {@code @EventListener}：它在 publishEvent 那一刻就执行，
 *       此时事务还没提交，属于"乐观发信"，回滚后就打脸了。</li>
 *   <li>{@code fallbackExecution = true}：万一某个调用点没有事务（比如后台手动补发），
 *       也照常执行，不至于事件凭空消失。</li>
 * </ul>
 *
 * <p>【为什么还要再包一层 try-catch？】
 * AFTER_COMMIT 阶段抛出的异常会冒泡到调用方——订单已经提交成功了，
 * 却因为发信失败给用户返回 500，属于本末倒置。这里兜住，只记日志。
 *
 * <p>这层"事件 → 监听器 → 发送"的结构还带来一个好处：
 * 以后要加"发短信""写站内信""推企业微信"，各写一个监听器即可，OrderService 一行不改。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMailListener {

    private final MailService mailService;

    /**
     * 处理订单邮件事件。
     *
     * @param event 事务提交后由 Spring 传入的事件对象
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderMail(OrderMailEvent event) {
        try {
            mailService.send(event);
        } catch (Exception e) {
            // MailService 内部已经把可预期的失败转成 mail_log 了，
            // 能走到这里的通常是"写日志也失败"这类极端情况，兜底记一笔即可。
            log.error("[邮件] 监听器异常兜底：订单 {} 的{}未处理",
                    event.orderNo(), event.type().getLabel(), e);
        }
    }
}
