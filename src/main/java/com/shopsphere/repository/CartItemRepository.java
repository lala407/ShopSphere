// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：CartItemRepository 骨架（接口声明 + import）
// 人工修改：三个查询方法由本人手写
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.repository;

import com.shopsphere.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 购物车项数据访问层。
 *
 * <p>[AI骨架] 三个方法都是"方法名即 SQL"，全靠派生查询自动生成。
 *
 * <p>嵌套属性写法：实体里的字段叫 user（对象），SQL 列叫 user_id。
 * 方法名里的 UserId 会被 Spring Data 解析为 user.id（先试整串 userId，
 * 不存在就按驼峰拆成 user + id），这是官方支持的嵌套属性约定。
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    // ==========================================================
    // [手写] TODO 1：按"用户 + 商品"查一行（加购时判断是否已存在）
    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    // [手写] TODO 2：查某用户的全部购物车项，id 倒序（新加的在前）
    List<CartItem> findByUserIdOrderByIdDesc(Long userId);
    // [手写] TODO 3：删除某用户的全部购物车项（清空用）
    void deleteByUserId(Long userId);

    // [手写 P1-7] 删除某商品在所有用户购物车里的引用（物理删除商品前先清购物车，
    // 否则会留下指向不存在商品的悬空购物车项）。
    // ⚠️ 派生删除方法自带事务才有意义：在 Controller 这种无事务上下文里直接调，
    // 必须标 @Transactional（deleteByUserId 在 Service 的事务方法里调，所以没标也能跑）
    @Transactional
    void deleteByProductId(Long productId);







}