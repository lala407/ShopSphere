// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：OrderRepository 骨架（接口声明 + import）
// 人工修改：两个查询方法由本人手写
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.repository;

import com.shopsphere.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // ==========================================================
    // [手写] TODO 1：按"订单 id + 用户 id"查订单（详情/付款时做归属校验）
   Optional<Order> findByIdAndUserId(Long id, Long userId);

    // [手写] TODO 2：查某用户的全部订单，按下单时间倒序（新的在前）
   List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);


}
