// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AccessAction 访问动作枚举（P0-2 浏览 / 购买日志）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.common;

/**
 * 访问日志的动作类型（对应 ex.html 要求「浏览日志 + 购买日志」）。
 *
 * <p>为什么分成"浏览类"和"购买类"两组？
 * 报告里要把两类日志分开讲：
 * <ul>
 *   <li><b>浏览日志</b>：VIEW_LIST（首页/列表）、VIEW_PRODUCT（商品详情）—— 反映"流量"；</li>
 *   <li><b>购买日志</b>：ADD_CART → CREATE_ORDER → PAY_ORDER —— 反映"转化漏斗"。</li>
 * </ul>
 * 两者串起来就是一条完整的用户行为链：看了什么 → 加购 → 下单 → 付款。
 *
 * <p>数据库存枚举名（VIEW_LIST），页面显示 {@link #getLabel()} 的中文，
 * 和 OrderStatus 的处理方式保持一致。
 */
public enum AccessAction {

    // ---------- 浏览类 ----------
    VIEW_LIST("浏览列表", "浏览"),
    VIEW_PRODUCT("查看商品", "浏览"),

    // ---------- 购买类 ----------
    ADD_CART("加入购物车", "购买"),
    CREATE_ORDER("提交订单", "购买"),
    PAY_ORDER("订单付款", "购买");

    private final String label;

    /** 所属大类：浏览 / 购买，前端分组统计用。 */
    private final String category;

    AccessAction(String label, String category) {
        this.label = label;
        this.category = category;
    }

    public String getLabel() {
        return label;
    }

    public String getCategory() {
        return category;
    }
}
