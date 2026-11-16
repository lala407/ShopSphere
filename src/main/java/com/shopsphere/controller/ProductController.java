// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：ProductController 骨架（类声明 + 方法签名）
// 人工修改：注解、字段、方法体由本人手写
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.controller;

import com.shopsphere.common.ProductStatus;
import com.shopsphere.entity.Product;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

/**
 * 商品浏览：首页列表 + 详情页。
 *
 * <p>[AI骨架] 方法签名已就位，注解、字段、方法体由本人手写。
 * <p>[AI修复] 删除了重复的 package + import 块（第二个 package 非法，
 *    导致 17 个"class/interface expected"编译错误）；lombok import 合并到上方。
 *
 * <p>本 Controller 直接调 Repository，不隔 Service——
 * 纯查询没有业务规则，隔层只会多写一个转发方法（计划书 6.4：MVP 不过度设计）。
 */

@Controller
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    /**
     * 首页 = 商品列表。
     * kw：关键字（商品名模糊），cid：分类筛选，page：页码（0 基）
     */
    @GetMapping("/")
    public String index(@RequestParam(required = false) String kw,
                        @RequestParam(required = false) Long cid,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {

        // 1. 分页请求：每页 8 条，id 倒序（新品在前）
        Pageable pageable = PageRequest.of(page, 8, Sort.by("id").descending());

        // 2. 多条件查询
        Page<Product> pageData = productRepository.searchOnSale(cid, kw, pageable);

        // 3. 装进 model 给页面用
        model.addAttribute("pageData", pageData);      // 商品列表 + 分页信息
        model.addAttribute("kw", kw);                  // 搜索框回显
        model.addAttribute("cid", cid);                // 分类高亮
        model.addAttribute("categories",
                categoryRepository.findAllByOrderBySortOrderAsc());  // 分类栏

        // 4. 渲染 list.html
        return "list";
    }

    /**
     * 商品详情：/product/5 → id=5
     *
     * <p>[AI辅助 P1-1 修复] 原来只判 id 是否存在，不判上下架状态——
     * 已下架商品拿到旧链接仍能打开详情页（页面上加购按钮还在，加购才被
     * CartService 拦截）。修复：非 ON_SALE 一律 404，与前台列表口径一致
     * （列表走 searchOnSale，本来就不展示下架商品）。
     */
    @GetMapping("/product/{id}")
    public String detail(@PathVariable Long id, Model model) {

        // 1. 查商品：查不到、或已下架，都按"不存在"处理，抛 404
        Product product = productRepository.findById(id)
                .filter(p -> p.getStatus() == ProductStatus.ON_SALE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // 2. 放进 model
        model.addAttribute("product", product);

        // 3. 渲染 detail.html
        return "detail";
    }
}