// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：OrderItemRepository 骨架（接口声明 + import）
// 人工修改：查询方法由本人手写
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.repository;

import com.shopsphere.common.OrderStatus;
import com.shopsphere.entity.OrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // ==========================================================
    // [手写] TODO：查某订单的全部明细
    List<OrderItem> findByOrderId(Long orderId);

    // [手写 TODO：P1-7 商品删除] 该商品是否被任何订单明细引用过。
    // 有引用 → 禁止物理删除（虽然 order_item.product_id 是裸 Long 没有外键，
    // 删除不会报错，但 Top5 统计按 productId 聚合，删了会让历史报表对不上号）。
    boolean existsByProductId(Long productId);

    /**
     * [AI完整实现 Day6] 热销商品统计：按"累计销量"降序（配合 Pageable 取前 5 即 Top5）。
     *
     * <p>逐句翻译（JPQL → SQL）：
     * <ul>
     *   <li>JOIN oi.order o —— 明细关联订单，为了拿到订单状态做过滤</li>
     *   <li>WHERE o.status IN :statuses —— 只统计"付过款"的订单
     *       （待付款没成交、已取消要剔除，这两种不算真实销量）</li>
     *   <li>GROUP BY productId, productName —— 同一商品在不同订单里的多条明细聚成一行</li>
     *   <li>ORDER BY SUM(quantity) DESC —— 销量最高的排最前</li>
     * </ul>
     *
     * <p>两个进阶点：
     * <ul>
     *   <li><b>接口投影</b>：返回的不是实体而是 HotProductStat 接口，
     *       JPQL 列别名（AS productId）与接口 getter 一一对应，Spring 自动填充（见接口注释）。</li>
     *   <li><b>Pageable 当 LIMIT 用</b>：JPQL 没有 LIMIT 关键字，
     *       调用方传 PageRequest.of(0, 5) 即等价于 LIMIT 5 —— "Top5"就是这么实现的。</li>
     * </ul>
     */
    @Query("""
            SELECT oi.productId     AS productId,
                   oi.productName   AS productName,
                   SUM(oi.quantity) AS totalQty
              FROM OrderItem oi
              JOIN oi.order o
             WHERE o.status IN :statuses
             GROUP BY oi.productId, oi.productName
             ORDER BY SUM(oi.quantity) DESC
            """)
    List<HotProductStat> findHotProducts(@Param("statuses") Collection<OrderStatus> statuses,
                                         Pageable pageable);

}
