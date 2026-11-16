// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AdminProductController 骨架（类声明、注解、字段）
// 人工修改：全部方法体由 AI 按学员确认补充实现（学员授权直接补全）
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.controller;

import com.shopsphere.common.ProductStatus;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Product;
import com.shopsphere.repository.CartItemRepository;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.OrderItemRepository;
import com.shopsphere.repository.ProductRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台·商品管理（计划书 3.6）：分页列表 / 新增 / 编辑 / 上下架（软删除）。
 *
 * <p>[AI骨架] 路由与方法签名已就位，方法体由你手写。
 * 页面模板（admin/product-list.html、admin/product-form.html）由 AI 完整实现，
 * 它们要求的 model 属性写在各方法 javadoc 里——key 必须一字不差。
 *
 * <p>类上的 @RequestMapping("/admin/product")：本类所有路径都以此为前缀。
 * 整个 /admin/** 已被 AdminInterceptor 拦截（仅 ADMIN 可进），这里不用判权限。
 *
 * <p>和前台 ProductController 一样直接调 Repository——
 * 后台 CRUD 没有跨表业务规则，不需要 Service 层（订单的发货/取消才有，走 OrderService）。
 */
@Controller
@RequestMapping("/admin/product")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;

    /**
     * 商品列表 GET /admin/product?page=
     *
     * model 需要两样：
     *   pageData    —— Page&lt;Product&gt;（后台看全部商品，含已下架；每页 10 条，id 倒序）
     *   categoryMap —— Map&lt;Long, String&gt;：分类 id → 分类名
     *                  （页面按这个 Map 把 categoryId 翻译成中文名显示）
     */
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        // 1) 分页：每页 10 条，id 倒序（findAll(Pageable) 是 JpaRepository 自带的）
        Pageable pageable = PageRequest.of(page, 10, Sort.by("id").descending());
        Page<Product> pageData = productRepository.findAll(pageable);

        // 2) categoryMap：分类 id → 分类名（页面按 id 显示中文名）
        Map<Long, String> categoryMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        // 3) 塞 model
        model.addAttribute("pageData", pageData);
        model.addAttribute("categoryMap", categoryMap);
        return "admin/product-list";
    }

    /**
     * 新增表单 GET /admin/product/new。
     *
     * model 需要：product（一个 new 出来的空 Product，模板靠它渲染表单）
     *            categories（分类下拉框，用按 sortOrder 升序那个方法）
     */
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("product", new Product());   // 空对象：模板靠 id==null 判断是新增
        model.addAttribute("categories", categoryRepository.findAllByOrderBySortOrderAsc());
        return "admin/product-form";
    }

    /**
     * 编辑表单 GET /admin/product/{id}/edit —— 与新增共用同一个模板。
     *
     * 模板怎么区分新增/编辑？看 product.id 是否为 null（隐藏域 + 标题判断）。
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        // 查不到抛业务异常 —— 不用 try-catch，GlobalExceptionHandler 会接住：
        // flash 提示 + 退回来源页（Day6 全局异常处理的实际效果）
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        model.addAttribute("product", product);
        model.addAttribute("categories", categoryRepository.findAllByOrderBySortOrderAsc());
        return "admin/product-form";
    }

    /**
     * 保存 POST /admin/product/save —— 新增与编辑共用。
     *
     * 表单字段自动绑定成 Product 对象（Spring 按字段名对应：
     * name/price/stock/categoryId/imageUrl/description/status + 隐藏域 id）。
     *
     * <p>[P2-2 新增] 服务端校验：此前只有前端 min="0.01" 这层纸糊的防线，
     * 直接 POST 可以把负价格/负库存写进库。现在两道防线：
     * ① 实体上的 Bean Validation 注解 + @Valid（消息见 Product 字段注释）；
     * ② categoryId 是裸 Long 无外键，额外查一次分类是否存在，
     * 否则商品会挂在一个不存在的分类下（前台按分类筛选永远查不到）。
     *
     * <p>校验失败回退到来源表单（新增→/new，编辑→/{id}/edit），flash 带错误信息；
     * 表单重开后是空白值，但红色提示写明了原因，重新填一遍即可（课程项目不做回填增强）。
     */
    @PostMapping("/save")
    public String save(@Valid Product product,
                       BindingResult bindingResult,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String msg = bindingResult.getFieldErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .distinct()
                    .collect(Collectors.joining("；"));
            redirectAttributes.addFlashAttribute("error", msg);
            return product.getId() == null
                    ? "redirect:/admin/product/new"
                    : "redirect:/admin/product/" + product.getId() + "/edit";
        }
        if (!categoryRepository.existsById(product.getCategoryId())) {
            redirectAttributes.addFlashAttribute("error", "所选分类不存在，请重新选择");
            return product.getId() == null
                    ? "redirect:/admin/product/new"
                    : "redirect:/admin/product/" + product.getId() + "/edit";
        }
        if (product.getId() == null) {
            // 新增：直接保存（createdAt 由 @PrePersist 自动填充）
            productRepository.save(product);
        } else {
            // 编辑：先查后改再存——表单里没有 sales / createdAt，
            // 直接 save(product) 会把这两个字段覆盖成 0/null
            Product old = productRepository.findById(product.getId())
                    .orElseThrow(() -> new IllegalArgumentException("商品不存在"));
            old.setName(product.getName());
            old.setPrice(product.getPrice());
            old.setStock(product.getStock());
            old.setCategoryId(product.getCategoryId());
            old.setImageUrl(product.getImageUrl());
            old.setDescription(product.getDescription());
            old.setStatus(product.getStatus());
            productRepository.save(old);
        }
        redirectAttributes.addFlashAttribute("msg", "保存成功");
        return "redirect:/admin/product";
    }

    /**
     * 上架/下架切换 POST /admin/product/{id}/toggle-status（软删除，计划书 1.2）。
     *
     * 为什么"删除商品"要做成改状态？
     * 商品已被订单引用过（快照里存了 productId），物理删除会丢历史；
     * 下架后前台 searchOnSale 自动查不到，零副作用。
     */
    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        // 在售 ↔ 下架 互切（软删除）
        if (product.getStatus() == ProductStatus.ON_SALE) {
            product.setStatus(ProductStatus.OFF_SHELF);
            redirectAttributes.addFlashAttribute("msg", "商品「" + product.getName() + "」已下架");
        } else {
            product.setStatus(ProductStatus.ON_SALE);
            redirectAttributes.addFlashAttribute("msg", "商品「" + product.getName() + "」已上架");
        }
        productRepository.save(product);
        return "redirect:/admin/product";
    }

    /**
     * 删除商品 POST /admin/product/{id}/delete（P1-7 新增，物理删除）。
     *
     * <p>什么时候能删、什么时候不能删？
     * <ul>
     *   <li>从未被任何订单买过（order_item 无 productId 引用）→ 物理删除，顺带清掉
     *       各用户购物车里对该商品的引用（避免留下悬空购物车项）；</li>
     *   <li>已被订单买过 → 拒绝删除。订单明细存的是价格快照（裸 Long，无外键），
     *       删商品本身不会让历史订单报错，但"删除"在语义上等于抹掉一个真实卖过的商品，
     *       历史报表（按 productId 聚合的 Top5）会对不上号。此时应改用下架——
     *       下架后前台 searchOnSale 查不到，历史与统计完整保留。</li>
     * </ul>
     *
     * <p>成功走 msg（绿色 alert），被拒走 error（红色 alert），都 flash 到列表页。
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在"));

        if (orderItemRepository.existsByProductId(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "商品「" + product.getName() + "」已有订单记录，删除会影响历史订单与统计，请改用下架");
        } else {
            cartItemRepository.deleteByProductId(id);
            productRepository.delete(product);
            redirectAttributes.addFlashAttribute("msg", "商品「" + product.getName() + "」已删除");
        }
        return "redirect:/admin/product";
    }
}
