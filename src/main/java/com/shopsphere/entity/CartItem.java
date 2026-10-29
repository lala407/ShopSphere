// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：CartItem 实体（含 @ManyToOne 关联）—— Day 4 学习重点，AI 写好并逐行讲解
// 人工修改：请读懂后自行复述（验收标准：能解释 @ManyToOne / @JoinColumn / uniqueConstraints）
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车项 cart_item。
 *
 * <p>本实体是 Day 4 的学习重点：第一次用真正的对象关联（@ManyToOne），
 * 而不是 Day 3 那种裸外键 Long categoryId。
 *
 * <p>两种写法对比：
 * <ul>
 *   <li>裸外键：private Long productId;          → 要展示商品名，得再查一次 Product</li>
 *   <li>对象关联：private Product product;        → item.getProduct().getName() 直接拿到</li>
 * </ul>
 *
 * <p>为什么 Day 1 的 Product 用裸 categoryId，这里却用关联？
 * 因为购物车页面每行都要显示商品名/单价/图片，用关联最省事；
 * 而商品列表页的分类只需要 id 做筛选，用裸字段更轻。
 * 这是"按使用场景选模型"，不是哪种更高级。
 */
@Entity
@Table(
        name = "cart_item",
        // UNIQUE(user_id, product_id)：同一用户同一商品只允许一行。
        // 作用：加购逻辑是"查到就 +1，查不到就新建"，万一同瞬间两个请求都判定
        // "查不到"，第二条 INSERT 会被数据库直接拒绝——这是并发下的最后一道保险。
        // 注意：约束名（uk_cart_user_product）自定义，方便报错时知道是哪条约束。
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cart_user_product",
                columnNames = {"user_id", "product_id"}
        )
)
@Data
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属用户。
     *
     * <p>@ManyToOne：多（购物车项）对一（用户）。
     * <p>@JoinColumn(name = "user_id")：告诉 Hibernate，外键列名是 user_id，
     *     不是默认的 user_id 之外的推导结果——**显式写出来最稳**。
     * <p>optional = false / nullable = false：该外键不允许为空（数据库层 NOT NULL）。
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 商品。
     *
     * <p>⚠️ fetch 模式说明（重要）：
     * @ManyToOne 默认是 EAGER（立即加载）——查购物车项时顺带把商品也查出来。
     * 为什么这里**没有**改成 LAZY？
     * 我们的 application.yml 配了 open-in-view: false（好实践），
     * 意味着模板渲染时数据库会话已关闭；若用 LAZY，Thymeleaf 访问
     * item.product.name 会抛 LazyInitializationException。
     * 购物车项数量很少，EAGER 的额外查询可以接受（MVP 取舍）。
     * 进阶做法是 LAZY + JPQL 的 JOIN FETCH 一次查完。
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * 数量。数据库 CHECK (quantity > 0) 由业务层保证（Hibernate 不生成 CHECK 约束），
     * "数量改为 0" 在 Service 层当作删除处理。
     */
    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** INSERT 前自动填时间（与 User/Product 同一套写法）。 */
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    /** UPDATE 前刷新 updatedAt（改数量时会用到）。 */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
