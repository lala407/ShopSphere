// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AccessLog 实体（表 access_log）—— 浏览 / 购买日志（P0-2）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.entity;

import com.shopsphere.common.AccessAction;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 访问日志 access_log：一次"值得记录的动作"一行。
 *
 * <p>【设计要点（答辩可讲）】
 * <ol>
 *   <li><b>只记 5 个动作，不记所有请求</b>：真正的 access log 会记录每个 HTTP 请求，
 *       但课程要求的是「浏览日志 + 购买日志」，所以只采集
 *       首页/列表、商品详情、加购、下单、付款这 5 个业务动作。
 *       好处是数据干净、查询快，后台页能直接当行为报表看。</li>
 *   <li><b>user_id 允许 NULL</b>：游客也能浏览，不属于任何用户。
 *       这时靠 session_id 把同一次会话串起来（做 PV/UV 的基础）。</li>
 *   <li><b>不建 @ManyToOne 关联</b>：日志是旁路审计数据，
 *       不该参与 User/Product 的加载；只留 target_id 冗余，查日志时免 join。</li>
 *   <li><b>索引</b>：日志表只增不减，查询永远是"按时间倒序 + 按动作筛选"，
 *       所以只建 created_at 和 action 两个索引，不为每个字段乱建。</li>
 * </ol>
 */
@Entity
@Table(
        name = "access_log",
        indexes = {
                // 后台日志页默认按时间倒序翻页
                @Index(name = "idx_access_created", columnList = "created_at"),
                // 按动作筛选（浏览 / 加购 / 下单 / 付款）
                @Index(name = "idx_access_action", columnList = "action"),
                // 按会话聚合（PV/UV、单次会话轨迹）
                @Index(name = "idx_access_session", columnList = "session_id")
        }
)
@Data
public class AccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录用户 id；游客为 NULL（允许匿名的关键）。 */
    @Column(name = "user_id")
    private Long userId;

    /** 会话 id：游客没有 user_id，只能靠它把一次访问串起来。 */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** 动作：VIEW_LIST / VIEW_PRODUCT / ADD_CART / CREATE_ORDER / PAY_ORDER。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private AccessAction action;

    /** 目标类型：PORTAL / PRODUCT / CART / ORDER。 */
    @Column(name = "target_type", length = 16)
    private String targetType;

    /** 目标 id：商品 id 或订单 id；首页没有目标，为 NULL。 */
    @Column(name = "target_id")
    private Long targetId;

    /** 客户端 IP。注意取 X-Forwarded-For 第一个（反向代理后才是真实 IP）。 */
    @Column(name = "ip", length = 64)
    private String ip;

    /** User-Agent，截断到 255（浏览器 UA 常见 120–200 字符）。 */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
