// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：HotProductStat —— 热销商品统计的接口投影（计划书 3.6 统计 SQL 属 AI 范围）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.repository;

/**
 * 热销商品统计结果 —— Spring Data JPA 的"接口投影"（Interface Projection）。
 *
 * <p>【它解决什么问题】
 * 热销 Top5 的查询结果是"商品id + 商品名 + 销量"三列，
 * 它不属于任何一张表（不是实体），只是个临时统计行。
 * 专门写一个实体类？不行，实体必须映射表。用 Object[] 接？取下标 magic number，难读。
 *
 * <p>【接口投影怎么用】
 * 只声明 getter 的接口 + JPQL 里给每列起别名（AS productId），
 * Spring 在运行时用动态代理实现这个接口，把列值填进去。
 * 约定：别名 == getter 去掉 get 后首字母小写（productId ↔ getProductId）。
 */
public interface HotProductStat {

    Long getProductId();

    String getProductName();

    /** 累计销量（SUM(quantity) 的结果）。SUM 对 int 列返回 Long，别写成 Integer。 */
    Long getTotalQty();
}
