// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AccessLogRepository —— 日志查询与计数（P0-2）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.repository;

import com.shopsphere.common.AccessAction;
import com.shopsphere.entity.AccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 访问日志仓库。
 *
 * <p>为什么多实现一个 {@link JpaSpecificationExecutor}？
 * 后台日志页有三个可选筛选条件（动作、起始日期、结束日期），
 * 组合起来是 2³ = 8 种情况。用 @Query 写 8 个方法太蠢，
 * 写一个 `(:action is null or ...)` 的 HQL 又容易在参数类型上踩坑；
 * Specification 是"按需拼 where"的标准做法，条件为空就跳过。
 */
public interface AccessLogRepository
        extends JpaRepository<AccessLog, Long>, JpaSpecificationExecutor<AccessLog> {

    /** 某种动作的总次数（后台四个统计卡用）。 */
    long countByAction(AccessAction action);

    /** 某个时间点之后的次数（"今日访问量"用）。 */
    long countByCreatedAtAfter(java.time.LocalDateTime time);
}
