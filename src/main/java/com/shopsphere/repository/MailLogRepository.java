// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：MailLogRepository（Day8 邮件通知）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.repository;

import com.shopsphere.entity.MailLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 邮件日志仓库。派生查询方法，不需要写 SQL。
 */
public interface MailLogRepository extends JpaRepository<MailLog, Long> {

    /**
     * 某订单的全部邮件记录（按发送顺序）。
     * 订单详情页用它渲染"邮件通知"一栏：下单成功的邮件在下，发货的在最后。
     */
    List<MailLog> findByOrderIdOrderByIdAsc(Long orderId);
}
