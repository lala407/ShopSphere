// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：UserRole 常量类（User.role 字段引用了它但工程里原本不存在，编译报错）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.common;

/**
 * 用户角色常量。
 *
 * <p>MVP 阶段 User.role 采用 String + 常量类（取舍见 User.role 字段的 Javadoc）：
 * 类型简单、模板里可直接字符串比较，后续需要时再升级为枚举。
 */
public final class UserRole {

    /** 普通顾客 */
    public static final String CUSTOMER = "CUSTOMER";

    /** 管理员（账号由种子 SQL 预置，不开放注册） */
    public static final String ADMIN = "ADMIN";

    // [手写] 私有构造器：常量类禁止实例化
    private UserRole() {
    }
}
