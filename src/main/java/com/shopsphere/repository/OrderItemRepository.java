// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：OrderItemRepository 骨架（接口声明 + import）
// 人工修改：查询方法由本人手写
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.repository;

import com.shopsphere.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // ==========================================================
    // [手写] TODO：查某订单的全部明细
    List<OrderItem> findByOrderId(Long orderId);

}
