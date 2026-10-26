// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：User 实体类骨架（@Entity/@Table/@Data 注解 + 类声明）
// 人工修改：全部字段、注解、Javadoc、@PrePersist 回调由本人手写
// AI 修复：删除误输入的 "/import java.time.LocalDateTime"（语法错误且重复）；
//          新建 common/UserRole 常量类供 role 字段引用
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.entity;

// [AI辅助] 补充跨包导入：UserRole 在 common 包，entity 包用必须显式 import
import com.shopsphere.common.UserRole;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应数据库表 t_user。
 *
 * <p>字段设计参考计划书 5.2 user 用户表：
 * <ul>
 *   <li>id          BIGINT PK AUTO_INCREMENT</li>
 *   <li>username    VARCHAR(50)  NOT NULL, UNIQUE</li>
 *   <li>password    VARCHAR(100) NOT NULL  —— 存 BCrypt 哈希（60 字符），长度给到 100 留余量</li>
 *   <li>email       VARCHAR(100) NULL</li>
 *   <li>role        VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER'</li>
 *   <li>status      TINYINT      NOT NULL DEFAULT 1  —— 1 正常，0 禁用</li>
 *   <li>created_at  DATETIME     NOT NULL</li>
 * </ul>
 *
 * <p>注意：
 * <ol>
 *   <li>表名用 t_user 而非 user —— user 在 PostgreSQL 是保留字，
 *       MySQL 下也和系统库 mysql.user 同名，加前缀最省心。</li>
 *   <li>@Data 会生成无参构造器，满足 JPA 反射创建实体的要求。</li>
 *   <li>密码字段不存明文，Service 层用 BCryptPasswordEncoder 加密后再 set。</li>
 * </ol>
 */
@Entity
// @Entity：标记本类为 JPA 实体，Hibernate 会把它映射到数据库表。
//   隐式要求：类不能 final、必须有无参构造器（@Data 已提供）。

@Table(name = "t_user")
// @Table：指定映射的表名。不写时默认用类名（User → user），
//   这里显式改成 t_user 规避关键字冲突。

@Data
// @Data（Lombok）：生成 getter / setter / toString / equals / hashCode
//   以及一个无参构造器（因为本类没有 final 字段和 @NonNull 字段）。
//   无参构造器是 JPA 的硬性要求，不能删。
public class User {

    // ==================== 1. 主键 ====================

    /**
     * 主键，数据库自增。
     *
     * <p>为什么用 Long 而不是 long：
     * 新建对象时 id 为 null，表示"尚未入库"；
     * save 之后 Hibernate 把自增值回填，id 变成具体数字。
     * 如果用 long，新建时默认 0，无法区分"未保存"和"id=0"。
     */
    @Id
    // @Id：标记本字段是主键，每个实体必须有且只有一个。

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // @GeneratedValue：主键生成策略。
    //   IDENTITY = 使用数据库自增（MySQL 的 AUTO_INCREMENT）。
    //   必须显式写 IDENTITY，不写默认 AUTO，
    //   MySQL 下会退化成 TABLE/SEQUENCE 模拟，多建一张序列表，性能差。

    private Long id;

    // ==================== 2. 用户名 ====================

    /**
     * 用户名，登录账号，全局唯一。
     *
     * <p>nullable = false → DDL 生成 NOT NULL；
     * length = 50       → VARCHAR(50)（不写默认 255，和设计不符）；
     * unique = true     → 生成 UNIQUE 约束，重复插入会抛
     *                      DataIntegrityViolationException，
     *                      业务层通常先 existsByUsername 判重给友好提示。
     */
    @Column(name = "username", nullable = false, length = 50, unique = true)
    private String username;

    // ==================== 3. 密码 ====================

    /**
     * 密码，存 BCrypt 哈希后的密文，绝不存明文。
     *
     * <p>长度必须 ≥ 60：BCrypt 哈希固定 60 字符（$2a$10$...），
     * 写 length = 50 会被数据库截断，导致登录永远失败——高频坑！
     * 这里给 100 留余量，未来换 Argon2 等更长算法也不怕。
     *
     * <p>加密流程在 Service 层：
     * <pre>
     *   String hash = passwordEncoder.encode(rawPassword);
     *   user.setPassword(hash);
     * </pre>
     */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    // ==================== 4. 邮箱 ====================

    /**
     * 邮箱，允许为空（用户可以不填）。
     *
     * <p>nullable 不写默认 true，符合设计中的 NULL。
     * 如果将来要求邮箱唯一，加 unique = true，
     * 但 MySQL 下多个 NULL 不冲突，符合"未填邮箱"的语义。
     */
    @Column(name = "email", length = 100)
    private String email;

    // ==================== 5. 角色 ====================

    /**
     * 角色：CUSTOMER（普通用户）/ ADMIN（管理员）。
     *
     * <p>字段初始化 = "CUSTOMER" 是关键：
     * 保证 new User() 时就有默认值，JPA save 会显式写 'CUSTOMER'，
     * 不会插入 NULL 违反 NOT NULL 约束。
     *
     * <p>为什么不用 columnDefinition = "... DEFAULT 'CUSTOMER'"：
     * JPA 的 INSERT 总是显式传值，数据库 DEFAULT 只在"列被省略"时生效，
     * 所以字段初始化才是 JPA 路径下保证默认值的正确方式。
     *
     * <p>可选升级：改成枚举更类型安全
     * <pre>
     *   {@literal @}Enumerated(EnumType.STRING)
     *   private UserRole role = UserRole.CUSTOMER;
     * </pre>
     * 本项目 MVP 阶段用 String + 常量类，灵活性更高。
     */
    @Column(name = "role", nullable = false, length = 20)
    private String role = UserRole.CUSTOMER;

    // ==================== 6. 状态 ====================

    /**
     * 状态：1 正常，0 禁用。
     *
     * <p>columnDefinition = "TINYINT" 是必须的：
     * Hibernate 默认把 Integer 映射为 INT，
     * 要精确生成 TINYINT 只能靠 columnDefinition 指定。
     * 如果不在意差 3 字节，去掉它生成 INT 也能跑。
     *
     * <p>为什么用 Integer 不用 boolean：
     * 计划书定义是 TINYINT，未来可能扩展更多状态（2 待审核等），
     * Integer 更灵活。
     *
     * <p>字段初始化 = 1 保证默认"正常"状态。
     */
    @Column(name = "status", nullable = false, columnDefinition = "TINYINT")
    private Integer status = 1;

    // ==================== 7. 创建时间 ====================

    /**
     * 创建时间。
     *
     * <p>类型用 LocalDateTime 而非 Date/Timestamp：
     * Java 8+ 时间 API，无时区歧义，JPA 3.1 原生支持映射到 DATETIME。
     *
     * <p>updatable = false：
     * Hibernate 生成 UPDATE 语句时排除此列，
     * 保证创建时间永不被覆盖，这是关键的安全约束。
     *
     * <p>值由 @PrePersist 回调自动填充（见方法）。
     * 不依赖数据库的 DEFAULT CURRENT_TIMESTAMP，
     * 因为 JPA 的 INSERT 会显式传值，DEFAULT 不会生效。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * JPA 生命周期回调：persist（INSERT）之前自动执行。
     *
     * <p>作用：给 createdAt 填当前时间。
     *
     * <p>为什么加 if (createdAt == null) 判断：
     * 如果业务代码或数据迁移手动设了时间，不要覆盖它；
     * 只有未设置时才自动填当前时间，兼容性更好。
     *
     * <p>注意：只写 @PrePersist 就够，本表没有 updated_at，
     * 不需要 @PreUpdate。
     */
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
