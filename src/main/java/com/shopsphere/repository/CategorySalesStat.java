// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：CategorySalesStat —— 分类销售占比的接口投影（P1-9 新增）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.repository;

import java.math.BigDecimal;

/**
 * 分类销售占比统计结果 —— 接口投影（用法见 {@link HotProductStat} 注释）。
 *
 * <p>明细表只存裸 productId（价格快照设计），要按分类聚合必须与商品表
 * ad hoc join（Hibernate 6 支持 JOIN 实体 ON 条件）拿 categoryId。
 * 注意：商品被物理删除（P1-7，仅限从未成交的商品）不会出现在这里——
 * 反过来说，能出现在统计里的商品都真实卖过，口径自洽。
 */
public interface CategorySalesStat {

    Long getCategoryId();

    /** 该分类的销售额 = SUM(oi.subtotal)（仅已付款口径）。 */
    BigDecimal getSales();

    /** 该分类的销量 = SUM(oi.quantity)。 */
    Long getTotalQty();
}
