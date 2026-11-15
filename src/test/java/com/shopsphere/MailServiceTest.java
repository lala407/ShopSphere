// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：MailServiceTest —— 邮件拼装与发送结果（SENT/FAILED）单元测试（Day8）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import com.shopsphere.common.MailStatus;
import com.shopsphere.common.MailType;
import com.shopsphere.entity.MailLog;
import com.shopsphere.event.OrderMailEvent;
import com.shopsphere.repository.MailLogRepository;
import com.shopsphere.service.MailService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

/**
 * 邮件服务测试：验证"真发"这条分支。
 *
 * <p>【为什么需要这个类】
 * 集成测试里的环境没配 SMTP 账号，邮件走的是 SKIPPED 分支，
 * **HTML 正文的拼装代码根本没被执行**。而那段代码用了 {@code String.formatted()}，
 * 少一个转义（比如 CSS 里的 100%）就会在运行期抛 IllegalFormatException。
 * 这里把 JavaMailSender 换成 Mock，就能在没有真实 SMTP 的情况下走完整个拼装 + 发送流程。
 */
@SpringBootTest(properties = {
        // 给一个假的发件账号，让 MailService 走"真发"分支
        "spring.mail.username=noreply@example.com",
        "shopsphere.mail.from-name=ShopSphere 测试商城"
})
@ActiveProfiles("test")
class MailServiceTest {

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private MailService mailService;

    @Autowired
    private MailLogRepository mailLogRepository;

    @BeforeEach
    void setUp() {
        mailLogRepository.deleteAll();
    }

    @Test
    @DisplayName("配置齐全时：邮件发出、日志记 SENT、正文含订单信息")
    void sendsMailAndMarksSent() throws Exception {

        // 准备一个真实的 MimeMessage 让 MimeMessageHelper 往里写（不真正联网）
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        given(mailSender.createMimeMessage()).willReturn(message);

        mailService.send(new OrderMailEvent(
                1L, "ORD202611150001", "buyer@example.com", "张三",
                new BigDecimal("198.00"), "测试商品 ×2；三体（全集三册） ×1",
                MailType.ORDER_SHIPPED, LocalDateTime.of(2026, 11, 15, 16, 30)));

        // 1) 真的调了 send
        then(mailSender).should().send(message);

        // 2) 日志状态是 SENT
        List<MailLog> logs = mailLogRepository.findByOrderIdOrderByIdAsc(1L);
        assertEquals(1, logs.size());
        assertEquals(MailStatus.SENT, logs.get(0).getStatus());
        assertEquals("buyer@example.com", logs.get(0).getToEmail());
        assertEquals(MailType.ORDER_SHIPPED, logs.get(0).getType());

        // 3) 标题与正文都正确（拼装没抛异常，说明 formatted 的参数个数/转义都对）
        String subject = message.getSubject();
        assertTrue(subject.contains("已发货"), "标题应包含邮件类型，实际：" + subject);

        String html = String.valueOf(message.getContent());
        assertTrue(html.contains("张三"), "正文应包含收货人");
        assertTrue(html.contains("ORD202611150001"), "正文应包含订单号");
        assertTrue(html.contains("查看订单详情"), "正文应包含查看订单链接");
    }

    @Test
    @DisplayName("SMTP 异常时：不抛给调用方，日志记 FAILED")
    void marksFailedWhenSmtpThrows() {

        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        given(mailSender.createMimeMessage()).willReturn(message);
        willThrow(new MailSendException("SMTP 连接超时")).given(mailSender).send(any(MimeMessage.class));

        // 关键：这里不应该抛异常 —— 邮件失败绝不能影响下单
        mailService.send(new OrderMailEvent(
                2L, "ORD202611150002", "buyer@example.com", "李四",
                new BigDecimal("99.00"), "测试商品 ×1",
                MailType.ORDER_PAID, LocalDateTime.now()));

        List<MailLog> logs = mailLogRepository.findByOrderIdOrderByIdAsc(2L);
        assertEquals(1, logs.size());
        assertEquals(MailStatus.FAILED, logs.get(0).getStatus());
        assertTrue(logs.get(0).getErrorMessage().contains("SMTP"),
                "失败原因应被记录下来：" + logs.get(0).getErrorMessage());
    }
}
