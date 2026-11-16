// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：OrderRepository 骨架（接口声明 + import）
// 人工修改：查询方法由本人手写（Day6 两个后台查询方法由 AI 按学员确认补充实现）
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.repository;

import com.shopsphere.common.OrderStatus;
import com.shopsphere.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // ==========================================================
    // 按"订单 id + 用户 id"查订单（详情/付款时做归属校验）
   Optional<Order> findByIdAndUserId(Long id, Long userId);

   /** [P2-7] 生成订单号时查重，避免唯一键冲突把下单打成 500。 */
   boolean existsByOrderNo(String orderNo);

    // 查某用户的全部订单，按下单时间倒序（新的在前）
   List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 后台订单列表 —— 查全部订单，按下单时间倒序
    //   （findAllBy 后面直接跟 OrderBy，中间没有条件字段）
    //   ⚠️ [P1-6 后] 后台列表已改用下方 search(...) 分页查询（订单多了全量加载会卡）；
    //   这两个全量方法保留给「导出 / 统计脚本」这类确实需要全部数据的场景。
    List<Order> findAllByOrderByCreatedAtDesc();

    // 后台订单筛选 —— 按状态查订单，按下单时间倒序
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    // ==========================================================
    // [AI辅助 P1-6 修复] 后台订单列表：多条件 + 分页
    // ==========================================================

    /**
     * 后台订单分页查询：状态 / 关键字 / 下单时间范围，四个条件都可为 null（= 不过滤）。
     *
     * <p>写法沿用 {@code ProductRepository.searchOnSale} 的核心技巧
     * {@code (:param IS NULL OR 字段 = :param)}：一条 JPQL 覆盖 2^4 = 16 种筛选组合，
     * 不用按组合写 16 个方法。参数为 null 时该条件恒真、自动失效。
     *
     * <p>关键字模糊匹配三个字段：订单号 / 收货人 / 下单用户名——
     * 管理员手上可能只有一个手机号截图里的单号，也可能只记得收货人姓名。
     * 隐式 join {@code o.user.username} 走的是内连接，因为 user 是
     * {@code optional = false}（每张订单必然有下单人），不会漏数据。
     *
     * <p>时间范围用"左闭右开"：{@code createdAt >= from} 且 {@code createdAt < to}。
     * 调用方把用户选的截止日期 +1 天再传进来，这样 {@code 2026-11-16} 这一天
     * 23:59:59 的订单也能被包含——如果用 {@code <= to} 且 to 是当天 00:00，
     * 当天下的单会全部查不到（日期筛选最经典的 off-by-one）。
     *
     * <p>排序不写在 JPQL 里，交给 Pageable 的 Sort（createdAt desc, id desc），
     * 这样同一个查询既能用于列表，也能复用成"只查条数"的 count 查询。
     *
     * @param status   订单状态，null = 全部状态
     * @param kw       关键字（订单号/收货人/用户名），null = 不过滤
     * @param from     下单时间下限（含），null = 不限
     * @param to       下单时间上限（不含），null = 不限
     * @param pageable 分页 + 排序
     */
    @Query("""
            SELECT o FROM Order o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:kw IS NULL
                   OR o.orderNo LIKE CONCAT('%', :kw, '%')
                   OR o.receiverName LIKE CONCAT('%', :kw, '%')
                   OR o.user.username LIKE CONCAT('%', :kw, '%'))
              AND (:from IS NULL OR o.createdAt >= :from)
              AND (:to   IS NULL OR o.createdAt <  :to)
            """)
    Page<Order> search(@Param("status") OrderStatus status,
                       @Param("kw") String kw,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to,
                       Pageable pageable);

    // ==========================================================
    // [AI完整实现 Day6] 统计查询（计划书 3.6：统计 SQL 由 AI 实现 + 讲解）
    // ==========================================================

    /**
     * 统计某几个状态下的订单数。
     *
     * <p>"已付款订单数"的业务口径：付过钱的订单 = PAID + SHIPPED + COMPLETED
     * （后两种也是付过款的，只是走到了更后面的状态）。
     * 派生查询里的 In 关键字对应 SQL 的 IN (...)，传 Collection 即可。
     */
    long countByStatusIn(Collection<OrderStatus> statuses);

    /**
     * 总销售额：已付款订单的 total_amount 求和。
     *
     * <p>COALESCE(SUM(x), 0)：一张订单都没有时 SUM 返回 NULL，
     * 而方法返回类型 BigDecimal 会拆箱 NPE；包一层 COALESCE 兜底为 0。
     */
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status IN :statuses")
    BigDecimal sumTotalAmountByStatusIn(@Param("statuses") Collection<OrderStatus> statuses);

    // ==========================================================
    // [AI辅助 P1-9 新增] 每日销售额趋势：按付款日期分组聚合
    // ==========================================================

    /**
     * 区间内每日销售额与成交单数（仅已付款口径，与总销售额同一标准）。
     *
     * <p>为什么按 paidAt（付款时间）而不是 createdAt 统计？——
     * 销售额在"付款"那一刻才成立：待付款订单随时可能取消，按下单时间会把
     * 未成交的单算进去，与「总销售额」「热销 Top5」口径打架。
     *
     * <p>日期怎么取？JPQL 没有跨库统一的 DATE() 函数，这里用最稳的写法：
     * {@code SUBSTRING(CAST(o.paidAt AS string), 1, 10)} —— 时间戳转字符串后
     * 截前 10 位（yyyy-MM-dd），MySQL 和 H2 都能正确翻译成 CAST AS CHAR / VARCHAR。
     * 别名 saleDate 直接用 String 接，Controller 侧再 parse 补零。
     *
     * <p>GROUP BY 里必须重复完整表达式而不是别名——严格 JPQL 不允许按别名分组。
     *
     * <p>时间范围沿用「左闭右开」约定（调用方传 截止日 +1 天的 00:00）。
     */
    @Query("""
            SELECT SUBSTRING(CAST(o.paidAt AS string), 1, 10) AS saleDate,
                   SUM(o.totalAmount) AS dailySales,
                   COUNT(o.id)        AS orderCount
              FROM Order o
             WHERE o.status IN :statuses
               AND o.paidAt IS NOT NULL
               AND o.paidAt >= :from
               AND o.paidAt <  :to
             GROUP BY SUBSTRING(CAST(o.paidAt AS string), 1, 10)
            """)
    List<DailySalesStat> sumDailySalesByStatusIn(@Param("statuses") Collection<OrderStatus> statuses,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);

}
