// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：UserRepository 骨架（接口声明 + import）
// 人工修改：查询方法由本人手写
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.repository;

import com.shopsphere.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户表的数据访问层（DAO）。
 *
 * <p>[AI骨架] 接口声明已就位，方法由本人手写。
 *
 * <p>先搞懂机制再写代码：
 * <ul>
 *   <li>你只需要"声明接口"，不用写实现类——Spring Data JPA 在启动时
 *       自动生成实现（动态代理），注册为 Bean，可直接 @Autowired 注入</li>
 *   <li>JpaRepository&lt;User, Long&gt;：尖括号里第一个是实体类，第二个是主键类型；
 *       继承它即免费获得 save/findAll/findById 等 17 个现成方法</li>
 *   <li>方法名即 SQL：findBy + 字段名 的驼峰组合，Spring Data 按名字自动推导查询条件</li>
 * </ul>
 */
public interface UserRepository extends JpaRepository<User, Long> {


    // 你来声明按用户名查用户的方法
   Optional<User> findByUsername(String username);

}
