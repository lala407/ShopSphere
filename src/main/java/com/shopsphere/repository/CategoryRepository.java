// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：CategoryRepository 骨架（接口声明 + import）
// 人工修改：查询方法由本人手写
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.repository;

import com.shopsphere.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 分类表数据访问层。
 *
 * <p>[AI骨架] 送分题——巩固"方法名即 SQL"约定。
 * 首页分类筛选栏需要按 sort_order 升序查全部分类。
 */
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // ==========================================================
    // [手写] TODO：声明一个方法，按 sortOrder 升序查所有分类
    //
    //   返回：List<Category>
    //   方法名拆解：findAllBy + OrderBy + SortOrder + Asc
    //
    //   提示：和 UserRepository 一样的"方法名即 SQL"约定，
    //   排序约定是 OrderBy + 字段名 + Asc/Desc
    // ==========================================================

    /**
     * 按 sortOrder 升序查询所有分类。
     *
     * <p>Spring Data JPA 运行时自动翻译为：
     * <pre>SELECT * FROM t_category ORDER BY sort_order ASC</pre>
     *
     * @return 按 sortOrder 升序排列的分类列表；没有数据时返回空 List（不是 null）
     */
    List<Category> findAllByOrderBySortOrderAsc();

}