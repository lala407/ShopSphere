// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：MailService —— SMTP 发信 + mail_log 留痕（Day8，ex.html "发送电子邮件确认发货"）
// 人工修改：无（SMTP 账号与授权码由学员自行配置，不写入代码）
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.service;

import com.shopsphere.common.MailStatus;
import com.shopsphere.common.MailType;
import com.shopsphere.entity.MailLog;
import com.shopsphere.event.OrderMailEvent;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * 邮件服务：把"订单事件"翻译成一封中文 HTML 邮件，发出去，并留一条 mail_log。
 *
 * <p>【设计要点（答辩可讲）】
 * <ol>
 *   <li><b>邮件不是交易的一部分</b>：发送失败只记 FAILED，绝不抛异常给调用方，
 *       更不允许因此回滚订单。电商里"通知"永远是可降级的旁路。</li>
 *   <li><b>三种结果都要落库</b>：SENT / FAILED / SKIPPED。
 *       没配 SMTP 账号时不报错、不刷屏，而是记一条 SKIPPED，
 *       订单详情页照样能显示"未发送（未配置发件账号）"——诚实且可演示。</li>
 *   <li><b>账号从环境变量来</b>：{@code spring.mail.username/password} 通过
 *       MAIL_USERNAME / MAIL_PASSWORD 注入，仓库里没有授权码。</li>
 *   <li><b>超时必设</b>：连接/读/写三个超时都在 application.yml 里配了 5s，
 *       否则 SMTP 一卡，下单请求会一直挂着。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final MailLogWriter mailLogWriter;

    /** 发件邮箱 = SMTP 登录账号（QQ/163 都要求 From 与登录账号一致）。 */
    @Value("${spring.mail.username:}")
    private String fromAddress;

    /** 发件人显示名，如 "ShopSphere 商城"。 */
    @Value("${shopsphere.mail.from-name:ShopSphere 商城}")
    private String fromName;

    /** 邮件里的"查看订单"链接前缀，部署后改成公网地址。 */
    @Value("${shopsphere.mail.base-url:http://localhost:8080}")
    private String baseUrl;

    /** 功能开关：false 时所有邮件直接记 SKIPPED（演示"关掉邮件"的效果）。 */
    @Value("${shopsphere.mail.enabled:true}")
    private boolean mailEnabled;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 发送一封订单通知邮件。**本方法不抛异常**——所有失败都转成 mail_log 里的一条记录。
     *
     * @param event 事件里带着发信所需的全部值（不含实体，避免延迟加载异常）
     */
    public void send(OrderMailEvent event) {

        MailLog record = new MailLog();
        record.setOrderId(event.orderId());
        record.setOrderNo(event.orderNo());
        record.setToEmail(event.toEmail());
        record.setType(event.type());

        // ---------- 情况 1：收件人没填邮箱 ----------
        if (event.toEmail() == null || event.toEmail().isBlank()) {
            save(record, MailStatus.SKIPPED, "用户未填写邮箱");
            return;
        }

        // ---------- 情况 2：功能被关掉 / 没配 SMTP 账号 ----------
        // 这两种都是"预期内"的，不当故障处理：不抛异常、只用 info 级别提示
        if (!mailEnabled) {
            save(record, MailStatus.SKIPPED, "邮件功能已关闭（shopsphere.mail.enabled=false）");
            log.info("[邮件] 已关闭，跳过：订单 {} 的{}", event.orderNo(), event.type().getLabel());
            return;
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            save(record, MailStatus.SKIPPED, "未配置发件账号（环境变量 MAIL_USERNAME）");
            log.info("[邮件] 未配置发件账号，跳过：订单 {} 的{}", event.orderNo(), event.type().getLabel());
            return;
        }

        // ---------- 情况 3：真发 ----------
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // 第二个参数 false = 不需要附件；编码固定 UTF-8，否则中文标题会乱码
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            // InternetAddress(地址, 显示名, 编码)：让收件人看到"ShopSphere 商城"而不是一坨邮箱
            helper.setFrom(new InternetAddress(fromAddress, fromName, "UTF-8").toString());
            helper.setTo(event.toEmail());
            helper.setSubject(subjectOf(event));
            helper.setText(htmlOf(event), true);   // true = 内容是 HTML

            mailSender.send(message);

            save(record, MailStatus.SENT, null);
            log.info("[邮件] {} 已发送：订单 {} → {}",
                    event.type().getLabel(), event.orderNo(), event.toEmail());

        } catch (Exception e) {
            // 失败原因截断到 200 字：数据库列长 200，异常全量堆栈塞不进也不该塞
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            save(record, MailStatus.FAILED, truncate(reason));
            log.warn("[邮件] {} 发送失败：订单 {} → {}，原因：{}",
                    event.type().getLabel(), event.orderNo(), event.toEmail(), reason);
        }
    }

    /** 落一条邮件日志。这里是唯一写 mail_log 的地方。 */
    private void save(MailLog record, MailStatus status, String errorMessage) {
        record.setStatus(status);
        record.setErrorMessage(errorMessage);
        // 日志写失败也不能影响业务，所以再包一层 try
        try {
            // ⚠️ 必须走 MailLogWriter 的 REQUIRES_NEW：
            //    本方法是在 AFTER_COMMIT 里被调用的，原事务已结束，
            //    直接 repository.save 会"不报错也不落库"（详见 MailLogWriter 注释）
            mailLogWriter.write(record);
        } catch (Exception e) {
            log.warn("[邮件] 写 mail_log 失败：订单 {}", record.getOrderNo(), e);
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 200 ? s : s.substring(0, 200);
    }

    // ==========================================================
    // 邮件内容：标题 + HTML 正文
    // ==========================================================

    private String subjectOf(OrderMailEvent event) {
        return switch (event.type()) {
            case ORDER_CREATED -> "【ShopSphere】下单成功，请完成付款（订单 " + event.orderNo() + "）";
            case ORDER_PAID -> "【ShopSphere】付款成功，正在为您备货（订单 " + event.orderNo() + "）";
            case ORDER_SHIPPED -> "【ShopSphere】您的订单已发货（订单 " + event.orderNo() + "）";
        };
    }

    /**
     * 拼一封 HTML 邮件。
     *
     * <p>为什么不用 Thymeleaf 模板？邮件客户端对 CSS 支持极差（Gmail 会剥掉 style 块），
     * 只能写内联样式；这种"一次性、纯拼接"的内容用文本块比模板文件更好读。
     *
     * <p>所有用户输入都过 {@link #esc(String)}：收货人、商品名是用户可控的，
     * 直接拼进 HTML 会有注入风险（哪怕只是邮件，也不该留口子）。
     */
    private String htmlOf(OrderMailEvent event) {

        String headline = switch (event.type()) {
            case ORDER_CREATED -> "下单成功，请尽快完成付款";
            case ORDER_PAID -> "付款成功，我们正在为您备货";
            case ORDER_SHIPPED -> "您的订单已发货";
        };

        String intro = switch (event.type()) {
            case ORDER_CREATED -> "感谢您的下单，我们已收到您的订单。完成付款后我们会立即安排发货。";
            case ORDER_PAID -> "已收到您的付款，仓库正在打包。发货后我们会再发一封邮件通知您。";
            case ORDER_SHIPPED -> "商品已交由快递发出，请注意查收。感谢您的耐心等待。";
        };

        String timeLabel = switch (event.type()) {
            case ORDER_CREATED -> "下单时间";
            case ORDER_PAID -> "付款时间";
            case ORDER_SHIPPED -> "发货时间";
        };

        String rows = row("订单号", esc(event.orderNo()))
                + row("订单金额", "¥ " + event.totalAmount())
                + row("收货人", esc(event.receiverName()))
                + row("商品明细", esc(event.itemSummary()))
                + row(timeLabel, event.happenedAt() == null ? "-" : TIME_FMT.format(event.happenedAt()));

        String link = baseUrl + "/orders/" + event.orderId();

        return """
                <div style="max-width:600px;margin:0 auto;padding:24px;font-family:'Microsoft YaHei',Arial,sans-serif;color:#333;">
                  <div style="border-left:4px solid #0d6efd;padding-left:12px;margin-bottom:20px;">
                    <div style="font-size:20px;font-weight:bold;color:#0d6efd;">ShopSphere 商城</div>
                    <div style="font-size:12px;color:#888;">网络应用架构设计与开发 · 课程实验</div>
                  </div>

                  <h2 style="font-size:18px;margin:0 0 12px 0;">%s</h2>
                  <p style="line-height:1.7;margin:0 0 16px 0;">%s</p>

                  <table style="width:100%%;border-collapse:collapse;font-size:14px;margin-bottom:20px;">
                    %s
                  </table>

                  <p style="margin:0 0 24px 0;">
                    <a href="%s" style="display:inline-block;background:#0d6efd;color:#fff;text-decoration:none;padding:10px 20px;border-radius:6px;">查看订单详情</a>
                  </p>

                  <p style="font-size:12px;color:#999;line-height:1.6;border-top:1px solid #eee;padding-top:12px;margin:0;">
                    本邮件由系统自动发送，请勿直接回复。<br>
                    如果你并未在 ShopSphere 下单，请忽略本邮件。
                  </p>
                </div>
                """.formatted(headline, intro, rows, link);
    }

    /** 一行"标签 / 值"，内联样式保证各邮件客户端都能正常显示。 */
    private String row(String label, String value) {
        return """
                <tr>
                  <td style="padding:8px 10px;background:#f7f7f9;border:1px solid #eee;width:110px;color:#666;">%s</td>
                  <td style="padding:8px 10px;border:1px solid #eee;">%s</td>
                </tr>
                """.formatted(label, value);
    }

    /**
     * HTML 转义：只处理 5 个危险字符，够用且不会把中文搞坏。
     * 顺序很重要——&amp; 必须第一个替换，否则会把后面替换出来的 &lt; 再转一遍。
     */
    private String esc(String raw) {
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
