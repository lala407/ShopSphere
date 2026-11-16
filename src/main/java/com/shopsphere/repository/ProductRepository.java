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
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * [AI完整实现 Day5] 条件更新扣库存 —— 防超卖的核心。
     *
     * <p>SQL 语义：UPDATE product SET stock = stock - :qty
     *             WHERE id = :pid AND stock >= :qty
     *
     * <p>为什么不能"先 findById 查库存，判断够不够，再 setStock(stock-qty)"？
     * 两个并发请求可能同时查到 stock=1、都判断"够"，然后都减 1 → 库存变成 -1（超卖）。
     * 把"判断"和"扣减"合并进同一条 UPDATE，由数据库的行锁保证原子性：
     * 只有 stock >= qty 时才会更新成功，否则**受影响行数为 0**。
     *
     * <p>返回值 int = 受影响行数。调用方判断：
     * <pre>if (affected == 0) throw new IllegalArgumentException("库存不足");</pre>
     *
     * <p>⚠️ 两个容易忽略的注解属性：
     * <ul>
     *   <li>flushAutomatically = true：执行本 UPDATE 前先把待写入的 INSERT 刷进库，
     *       否则 JPA 可能先执行 UPDATE 后写订单，导致库存已扣但订单没落库。</li>
     *   <li>clearAutomatically = true：UPDATE 绕过了一级缓存（持久化上下文），
     *       执行后清空缓存，保证后续读到的 stock 是新值，而不是缓存里的旧快照。</li>
     * </ul>
     *
     * <p>⚠️ @Modifying 查询必须在事务中执行（调用方 OrderService.createOrder 有 @Transactional）。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Product p
               SET p.stock = p.stock - :qty
             WHERE p.id = :pid
               AND p.stock >= :qty
            """)
    int deductStock(@Param("pid") Long productId, @Param("qty") int quantity);

    /**
     * [AI辅助 P1-8 新增] 统计某分类下的商品数。
     * 后台分类删除前的引用校验：有商品的分类不允许删，只能先移走商品再删。
     */
    long countByCategoryId(Long categoryId);

    /**
     * [AI完整实现 Day6] 取消订单回补库存：stock = stock + qty。
     *
     * <p>和 deductStock 对照看：
     * 加库存是"无条件更新"——不需要 WHERE stock >= :qty，
     * 因为把库存加多永远不会失败（不像扣库存有"不够扣"的情况）。
     * 两个注解属性的作用与 deductStock 完全相同。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Product p
               SET p.stock = p.stock + :qty
             WHERE p.id = :pid
            """)
    int restoreStock(@Param("pid") Long productId, @Param("qty") int quantity);

    /**
     * [AI辅助 P1-2 修复] 付款成功后累加销量：sales = sales + qty。
     *
     * <p>口径：前台「已售 N 件」 = 已付款成交量，与后台统计页「热销 Top5」
     * （order_item 按 PAID_STATUSES 聚合）保持一致。
     * 为什么不在下单（扣库存）时累加？——待付款订单随时可能被取消，
     * 下单口径会让「已售」虚高且与 Top5 打架；付款口径天然排除未成交订单。
     *
     * <p>与 restoreStock 一样是"无条件更新"——累加不存在"加不进去"的情况。
     * 注意这里**没有**开 clearAutomatically：pay() 紧接着还要读 order.user
     * 发邮件事件，清掉持久化上下文有懒加载风险，没必要冒这个险。
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Product p
               SET p.sales = p.sales + :qty
             WHERE p.id = :pid
            """)
    int incrementSales(@Param("pid") Long productId, @Param("qty") int quantity);

    /**
     * [AI辅助 P1-2 修复] 已付款订单取消时回退销量（与 incrementSales 对称）。
     *
     * <p>CASE WHEN 防负数：正常流程里 sales 只在付款时加过、取消时减回去，
     * 不会为负；但 history 上可能存在"修复前付款、修复后取消"的订单，
     * sales 没加过却要减，这里兜底减到 0 为止。
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Product p
               SET p.sales = CASE WHEN p.sales >= :qty THEN p.sales - :qty ELSE 0 END
             WHERE p.id = :pid
            """)
    int decrementSales(@Param("pid") Long productId, @Param("qty") int quantity);
}
