// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：ProductRepository（多条件动态分页查询，计划书 3.3 分工属 AI）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.repository;

import com.shopsphere.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 商品表数据访问层。
 *
 * <p>[AI完整实现] 多条件动态分页查询是计划书 3.3 中 AI 负责的部分
 * （动态条件 + 分页属于进阶内容，AI 写并讲解）。
 *
 * <p>核心技巧：(:param IS NULL OR 字段 = :param)
 * → 参数为 null 时该条件恒真、自动失效；不为 null 时正常过滤。
 * 一条 JPQL 覆盖所有筛选组合，不用写 2^n 个方法。
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 前台商品列表：只查"在售"商品，支持分类 + 关键字 + 分页。
     *
     * <p>枚举字面量：JPQL 里枚举必须写全限定名
     * （com.shopsphere.common.ProductStatus.ON_SALE），不能写字符串。
     *
     * <p>CONCAT('%', :kw, '%')：JPQL 标准写法，等价 SQL 的 LIKE '%kw%'（模糊匹配）。
     *
     * @param cid      分类 ID，null = 全部分类
     * @param kw       商品名关键字，null = 不按名称过滤
     * @param pageable 分页参数（页码从 0 开始 + 每页条数 + 排序）
     * @return Page——不只是 List！还含总页数/当前页/是否首页末页，分页条直接用
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.status = com.shopsphere.common.ProductStatus.ON_SALE
              AND (:cid IS NULL OR p.categoryId = :cid)
              AND (:kw  IS NULL OR p.name LIKE CONCAT('%', :kw, '%'))
            """)
    Page<Product> searchOnSale(@Param("cid") Long cid,
                               @Param("kw") String kw,
                               Pageable pageable);
}
