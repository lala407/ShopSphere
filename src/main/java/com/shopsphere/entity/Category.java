// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：Category 实体类骨架
// 人工修改：全部字段与注解由本人手写
// AI 修复：字段 sort_order 改名 sortOrder（Java 驼峰规范，列名不变）
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 分类表 category
 * <p>
 * 最简单的实体，只有 3 个字段。
 */
@Entity
@Table(name = "category")
@Data
public class Category {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    //分类名
    @Column(name = "name",nullable = false,length = 50,unique = true)
    private  String name;
    // [AI辅助] 字段名 sort_order 改为 sortOrder（Java 驼峰规范，Lombok 才能生成 getSortOrder），
    //          数据库列名不受影响——由 @Column(name = "sort_order") 显式映射
    //前台展示顺序
    @Column(name = "sort_order", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int sortOrder = 0;

}
