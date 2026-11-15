// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：HealthController —— /healthz 探活端点（供云端定时 curl 监控用）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 探活端点（P0-3「3 个月可用」）。
 *
 * <p>为什么不满足于首页返回 200？
 * 首页 200 只能说明「Tomcat 还活着」。数据库连不上时首页会抛 500 没错，
 * 但更常见的坑是连接池耗尽、MySQL 容器被 OOM 杀掉后应用还在硬撑，
 * 这时有一个明确的、不查业务表的探针最省事：一条 {@code SELECT 1} 打到库上，
 * 通了才算 UP。
 *
 * <p>返回约定（方便监控脚本用 HTTP 状态码判断，不用解析 JSON）：
 * <ul>
 *   <li>200 + 纯文本 {@code UP db=<耗时>ms} —— 应用和数据库都正常</li>
 *   <li>503 + 纯文本 {@code DOWN ...} —— 数据库不可用（应用自己还活着）</li>
 * </ul>
 *
 * <p>安全说明：本端点不返回任何业务数据、不返回连接串、不返回堆栈细节，
 * 只暴露「通/不通」，可以公网访问。已在 {@code WebConfig} 里排除访问日志拦截器，
 * 避免监控每 30 分钟往 access_log 里写垃圾。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    /** JdbcTemplate 由 spring-boot-starter-data-jpa 自动装配（底层是 HikariCP 连接池）。 */
    private final JdbcTemplate jdbcTemplate;

    @GetMapping(value = "/healthz", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> healthz() {
        long start = System.currentTimeMillis();
        try {
            // 只做一次最轻量的往返：验证「连接拿得到 + SQL 能执行」
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long cost = System.currentTimeMillis() - start;
            return ResponseEntity.ok("UP db=" + cost + "ms");
        } catch (Exception e) {
            // 探针失败要留日志（监控抓不到根因时，至少 docker logs 里能看到）
            log.warn("健康检查失败：数据库不可用", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("DOWN db=unavailable");
        }
    }
}
