// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AdminCategoryController —— 后台分类管理（P1-8 新增）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.controller;

import com.shopsphere.entity.Category;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * 后台·分类管理（P1-8 新增，计划书 3.6）。
 *
 * <p>修复前：分类只能改 data.sql 再重启——新增一个分类要动代码，显然不算"管理"。
 * 现在提供：列表（含每类商品数）+ 新增 + 改名 + 排序 + 删除（带引用校验）。
 *
 * <p>和 AdminProductController 一样直接调 Repository——分类 CRUD 没有跨表业务规则，
 * 唯一的"规则"是删除前查商品数（一条 count 就够，不需要 Service 层）。
 *
 * <p>为什么删除要校验引用？product.categoryId 是裸 Long（无外键），
 * 强删不会报错，但会留下一批 categoryId 指向不存在分类的"孤儿商品"——
 * 前台按分类筛选时这些商品永远查不到，等于静默丢单。有商品的分类应先移走商品再删。
 *
 * <p>整个 /admin/** 已被 AdminInterceptor 拦截（仅 ADMIN 可进），这里不判权限。
 */
@Controller
@RequestMapping("/admin/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    /**
     * 分类列表 GET /admin/category。
     *
     * model：
     *   categories    —— List&lt;Category&gt;，按 sortOrder 升序（与前台筛选栏同序）
     *   productCounts —— Map&lt;Long, Long&gt;：分类 id → 该分类下商品数（删除校验的可视化）
     */
    @GetMapping
    public String list(Model model) {
        var categories = categoryRepository.findAllByOrderBySortOrderAsc();
        Map<Long, Long> productCounts = new HashMap<>();
        for (Category c : categories) {
            productCounts.put(c.getId(), productRepository.countByCategoryId(c.getId()));
        }
        model.addAttribute("categories", categories);
        model.addAttribute("productCounts", productCounts);
        return "admin/category-list";
    }

    /**
     * 新增分类 POST /admin/category/add（name + sortOrder）。
     *
     * <p>重名校验：category.name 有 unique 约束，先 findByName 查一次再插，
     * 给出友好提示而不是让 DataIntegrityViolationException 变成 500 页。
     */
    @PostMapping("/add")
    public String add(@RequestParam String name,
                      @RequestParam(defaultValue = "0") int sortOrder,
                      RedirectAttributes redirectAttributes) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "分类名不能为空");
            return "redirect:/admin/category";
        }
        if (categoryRepository.findByName(trimmed).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "分类「" + trimmed + "」已存在");
            return "redirect:/admin/category";
        }
        Category category = new Category();
        category.setName(trimmed);
        category.setSortOrder(sortOrder);
        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("msg", "分类「" + trimmed + "」已创建");
        return "redirect:/admin/category";
    }

    /**
     * 修改分类 POST /admin/category/{id}/update（name + sortOrder 一起提交，一行一存）。
     *
     * <p>改名也要过重名校验——注意排除自己（名字没改只调排序时，findByName 命中的就是自己）。
     */
    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam(defaultValue = "0") int sortOrder,
                         RedirectAttributes redirectAttributes) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("分类不存在"));
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "分类名不能为空");
            return "redirect:/admin/category";
        }
        categoryRepository.findByName(trimmed)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("分类名「" + trimmed + "」已被其他分类使用");
                });
        category.setName(trimmed);
        category.setSortOrder(sortOrder);
        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("msg", "分类「" + trimmed + "」已更新");
        return "redirect:/admin/category";
    }

    /**
     * 删除分类 POST /admin/category/{id}/delete。
     *
     * <p>有商品引用则拒绝（见类注释里"孤儿商品"的说明），提示先移走商品。
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("分类不存在"));
        long count = productRepository.countByCategoryId(id);
        if (count > 0) {
            redirectAttributes.addFlashAttribute("error",
                    "分类「" + category.getName() + "」下还有 " + count + " 件商品，请先移走或删除商品再删除分类");
        } else {
            categoryRepository.delete(category);
            redirectAttributes.addFlashAttribute("msg", "分类「" + category.getName() + "」已删除");
        }
        return "redirect:/admin/category";
    }
}
