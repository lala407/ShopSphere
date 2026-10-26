// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：ProductStatus 枚举（Product.status 字段引用了它但工程里原本不存在，编译报错）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.common;

/**
 * 商品上下架状态。
 *
 * <p>配合 Product.status 上的 {@code @Enumerated(EnumType.STRING)} 使用：
 * 数据库存的是字符串 "ON_SALE"/"OFF_SHELF" 而不是 0/1，
 * 查库时人眼可读，新增状态也不会错位。
 *
 * <p>"删除商品" = 把状态改为 OFF_SHELF（软删除），见计划书 1.2 / 3.6。
 */
public enum ProductStatus {
    /** 在售：前台可见、可加购 */
    ON_SALE,
    /** 下架：前台不可见、不可加购（软删除） */
    OFF_SHELF
}
