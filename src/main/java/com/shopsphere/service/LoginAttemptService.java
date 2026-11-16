// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：LoginAttemptService —— 登录失败次数限制（P2-5）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流：同一用户名连续失败 5 次，锁定 5 分钟。
 *
 * <p><b>为什么需要</b>：没有限制的登录接口等于给了攻击者离线撞库的在线版本——
 * 密码字典跑一遍就能试出弱口令（本项目演示账号 demo/demo123 就是弱口令）。
 *
 * <p><b>为什么放在内存里</b>：课程项目单实例部署，{@link ConcurrentHashMap} 足够；
 * 真要上多实例，把这张表换成 Redis 即可（接口不变）。用内存的另一个好处是
 * 不用为「登录失败」建库表，重启即清零，不会有历史包袱。
 *
 * <p>锁定期间即使密码正确也拒绝——否则攻击者可以用「猜对即通过」绕过次数限制。
 */
@Service
public class LoginAttemptService {

    /** 允许的连续失败次数。 */
    static final int MAX_FAILURES = 5;
    /** 触发上限后的锁定时长。 */
    static final Duration LOCK_DURATION = Duration.ofMinutes(5);

    /** key = 小写用户名（用户名本来就大小写敏感，这里统一小写避免绕过）。 */
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 是否处于锁定期。 */
    public boolean isBlocked(String username) {
        Attempt attempt = attempts.get(key(username));
        if (attempt == null || attempt.lockedUntil() == null) {
            return false;
        }
        if (Instant.now().isAfter(attempt.lockedUntil())) {
            attempts.remove(key(username));   // 锁定期已过，顺手清掉，避免 map 无限增长
            return false;
        }
        return true;
    }

    /** 锁定期还剩多少秒（用于提示文案）。 */
    public long remainingSeconds(String username) {
        Attempt attempt = attempts.get(key(username));
        if (attempt == null || attempt.lockedUntil() == null) {
            return 0;
        }
        long seconds = Duration.between(Instant.now(), attempt.lockedUntil()).toSeconds();
        return Math.max(seconds, 0);
    }

    /** 记一次失败；达到上限则开始锁定。 */
    public void onFailure(String username) {
        String key = key(username);
        attempts.compute(key, (k, old) -> {
            int failures = (old == null ? 0 : old.failures()) + 1;
            if (failures >= MAX_FAILURES) {
                return new Attempt(failures, Instant.now().plus(LOCK_DURATION));
            }
            return new Attempt(failures, null);
        });
    }

    /** 登录成功：清零，避免「白天输错几次、晚上正常登录仍被锁」。 */
    public void onSuccess(String username) {
        attempts.remove(key(username));
    }

    /** 测试用：清空所有计数。 */
    public void reset() {
        attempts.clear();
    }

    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    /** failures 为累计失败次数；lockedUntil 非空表示锁定中。 */
    private record Attempt(int failures, Instant lockedUntil) {
    }
}
