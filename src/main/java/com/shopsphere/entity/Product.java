// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：Product 实体类骨架
// 人工修改：全部字段与注解由本人手写
// AI 修复：新建 common/ProductStatus 枚举；补充 @PrePersist/@PreUpdate 时间自动填充
//          （原文件两个时间字段 nullable=false 却无赋值入口，INSERT 会违反非空约束）
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.entity;

// [AI辅助] 补充跨包导入：ProductStatus 在 common 包，entity 包用必须显式 import
import com.shopsphere.common.ProductStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品表 product
 * <p>
 * 字段最多的实体。参考计划书 5.2。
 * <p>
 * 注意事项：
 * - price 是金额，必须用 BigDecimal！double 会算错钱
 * - status 我们用字符串 ON_SALE / OFF_SHELF 表示上下架，"删除"实际是改状态（软删除）
 * - category_id 是外键，先别写 @ManyToOne 关联（Day 3 商品浏览时再引，避免现在麻烦），
 *   先用一个 Long categoryId 字段 + @Column(name = "category_id") 即可
 */
@Entity
@Table(name = "product")
@Data
public class Product {



    // ========== TODO：写字段 ==========
    // 参考计划书 5.2 product 商品表：

    // 1) id           BIGINT PK AUTO_INCREMENT
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private  Long id;

    // 2) name         VARCHAR(100) NOT NULL
    @Column(name = "name",length = 100,nullable = false)
    private  String name;
    // 3) description  TEXT NULL               —— 描述，@Column(columnDefinition = "TEXT")
    @Column(name = "description",columnDefinition = "TEXT")
    private String description;
    // 4) price        DECIMAL(10,2) NOT NULL  —— 金额！BigDecimal！
    @Column(name = "price",nullable = false,precision = 10,scale = 2)
    private BigDecimal price;
    // 5) stock        INT NOT NULL DEFAULT 0
    @Column(name = "stock", nullable = false)
    private Integer stock = 0;
    // 6) category_id  BIGINT NOT NULL          —— 先写 Long categoryId
    @Column(name = "category_id", nullable = false)
    private Long categoryId;
    // 7) image_url    VARCHAR(255) NULL
    @Column(name = "image_url", length = 255)
    private String imageUrl;
    // 8) status       VARCHAR(20) NOT NULL DEFAULT 'ON_SALE'
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ON_SALE;
    // 9) sales        INT NOT NULL DEFAULT 0   —— 累计销量，可裁剪字段
    @Column(name = "sales", nullable = false)
    private Integer sales = 0;
    // 10) created_at  DATETIME NOT NULL
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    // 11) updated_at  DATETIME NOT NULL
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * INSERT 前自动填 createdAt / updatedAt。
     *
     * <p>[AI辅助] 该方法由 AI 补充：原文件 createdAt/updatedAt 均为 nullable = false，
     * 却没有任何赋值入口，INSERT 时会因 NULL 直接违反非空约束。
     * 写法与 User.prePersist 保持一致。
     */
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * UPDATE 前刷新 updatedAt。
     *
     * <p>[AI辅助] 该方法由 AI 补充：保证每次修改商品（改价、改库存）时间戳自动更新。
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
