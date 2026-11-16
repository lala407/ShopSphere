// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：DailySalesStat —— 每日销售额统计的接口投影（P1-9 新增）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.repository;

import java.math.BigDecimal;

/**
 * 每日销售额统计结果 —— 接口投影（用法见 {@link HotProductStat} 注释）。
 *
 * <p>saleDate 是「把 paidAt 转成字符串再截前 10 位」得到的 yyyy-MM-dd，
 * 直接用 String 接——跨数据库（MySQL / H2）最稳，Controller 侧再 parse 成 LocalDate 补零。
 */
public interface DailySalesStat {

    String getSaleDate();

    /** 当日销售额 = SUM(total_amount)（仅已付款口径）。 */
    BigDecimal getDailySales();

    /** 当日成交订单数 = COUNT(*)。SUM/COUNT 结果类型是 Long。 */
    Long getOrderCount();
}
