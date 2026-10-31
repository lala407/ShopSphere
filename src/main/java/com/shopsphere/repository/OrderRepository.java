// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：OrderRepository 骨架（接口声明 + import）
// 人工修改：查询方法由本人手写（Day6 两个后台查询方法由 AI 按学员确认补充实现）
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.repository;

import com.shopsphere.common.OrderStatus;
import com.shopsphere.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // ==========================================================
    // [手写] TODO 1：按"订单 id + 用户 id"查订单（详情/付款时做归属校验）
   Optional<Order> findByIdAndUserId(Long id, Long userId);

    // [手写] TODO 2：查某用户的全部订单，按下单时间倒序（新的在前）
   List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    // [手写 Day6] TODO 3：后台订单列表 —— 查全部订单，按下单时间倒序
    //   （findAllBy 后面直接跟 OrderBy，中间没有条件字段）
    List<Order> findAllByOrderByCreatedAtDesc();

    // [手写 Day6] TODO 4：后台订单筛选 —— 按状态查订单，按下单时间倒序
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

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

}
