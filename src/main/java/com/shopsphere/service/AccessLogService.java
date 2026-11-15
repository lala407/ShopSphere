// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AccessLogService —— 写日志（失败不影响主流程）+ 查询（P0-2）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.service;

import com.shopsphere.common.AccessAction;
import com.shopsphere.entity.AccessLog;
import com.shopsphere.repository.AccessLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 访问日志服务：负责"写"和"查"两件事。
 *
 * <p>【最关键的一条：日志写失败绝不能影响业务】
 * 记日志是旁路行为——用户来买东西，不能因为日志表写不进去就下单失败。
 * 所以 {@link #record} 内部把异常全吃掉，只打一条 warn。
 * 这也是为什么没有把 save 直接写在拦截器里：拦截器抛异常会中断请求链。
 *
 * <p>【为什么不在 Service 里 @Async】
 * 课程项目并发量很小，同步写一行 INSERT 的代价可以忽略；
 * 而 @Async 会让"刚下单、日志还没落库"成为常态，测试和演示都不好断言。
 * 真要优化，正确做法是攒批 + 异步队列，不是给每个请求开一个线程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogRepository accessLogRepository;

    /** UA 超过列长的部分直接丢掉（浏览器 UA 常见的 120–200 字符，够用）。 */
    private static final int UA_MAX = 255;

    /**
     * 记一条访问日志。**本方法不抛异常**。
     *
     * @param action     动作类型
     * @param targetType 目标类型（PORTAL / PRODUCT / CART / ORDER）
     * @param targetId   目标 id，没有就传 null
     * @param request    当前请求（取 session、IP、UA）
     */
    public void record(AccessAction action, String targetType, Long targetId,
                       HttpServletRequest request) {
        try {
            AccessLog entry = new AccessLog();
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);

            // 游客没有 userId，sessionId 就是唯一的"身份"线索
            HttpSession session = request.getSession(false);
            if (session != null) {
                entry.setSessionId(truncate(session.getId(), 64));
                Object uid = session.getAttribute("userId");
                if (uid instanceof Long id) {
                    entry.setUserId(id);
                }
            }

            entry.setIp(resolveIp(request));
            entry.setUserAgent(truncate(request.getHeader("User-Agent"), UA_MAX));

            accessLogRepository.save(entry);

        } catch (Exception e) {
            // 只记录，不抛出：日志是旁路，不能反过来伤害主流程
            log.warn("[访问日志] 写入失败（已忽略）：action={} uri={}",
                    action, request.getRequestURI(), e);
        }
    }

    /**
     * 后台日志页的分页查询：动作 + 日期范围都是可选的。
     *
     * @param action 动作，null = 全部
     * @param from   起始日期（含），null = 不限
     * @param to     结束日期（含），null = 不限
     */
    public Page<AccessLog> search(AccessAction action, LocalDate from, LocalDate to,
                                  int page, int size) {
        // Specification 按需拼条件：为空的条件直接不拼，避免 8 种组合写 8 个方法
        Specification<AccessLog> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> conditions = new ArrayList<>();
            if (action != null) {
                conditions.add(cb.equal(root.get("action"), action));
            }
            if (from != null) {
                // 日期是"天"，要转成时间边界：>= 当天 00:00:00
                conditions.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                // 结束日期含当天：< 次日 00:00:00（比 <= 当天 23:59:59 更严谨，不会漏毫秒）
                conditions.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }
            return conditions.isEmpty()
                    ? cb.conjunction()
                    : cb.and(conditions.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        // 按时间倒序；时间相同时再按 id 倒序，保证翻页顺序稳定
        Pageable pageable = PageRequest.of(Math.max(page, 0), size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        return accessLogRepository.findAll(spec, pageable);
    }

    /** 各动作的总次数，给后台四个指标卡用。 */
    public Map<AccessAction, Long> countByAction() {
        Map<AccessAction, Long> result = new EnumMap<>(AccessAction.class);
        for (AccessAction action : AccessAction.values()) {
            result.put(action, accessLogRepository.countByAction(action));
        }
        return result;
    }

    /** 今日新增日志条数（判断"日志是不是还在记"的最快信号）。 */
    public long countToday() {
        return accessLogRepository.countByCreatedAtAfter(LocalDate.now().atStartOfDay());
    }

    /**
     * 取真实客户端 IP。
     *
     * <p>部署在 Nginx / 云负载均衡后面时，request.getRemoteAddr() 拿到的是代理的 IP，
     * 真实 IP 在 X-Forwarded-For 里；它有可能是 "客户端,代理1,代理2"，取第一个。
     */
    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private String truncate(String raw, int max) {
        if (raw == null) {
            return null;
        }
        return raw.length() <= max ? raw : raw.substring(0, max);
    }
}
