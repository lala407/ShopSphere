// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：CartService 骨架（类声明、注解、字段 + 各方法 TODO 步骤）
// 人工修改：全部方法体由本人手写
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.service;

import com.shopsphere.common.ProductStatus;
import com.shopsphere.entity.CartItem;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.repository.CartItemRepository;
import com.shopsphere.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    /**
     * 加入购物车：已存在则数量累加，不存在则新建一行。
     */
    @Transactional
    public void addToCart(Long userId, Long productId, int quantity) {

        // [手写] 1) 数量校验
        if (quantity < 1) {
            throw new IllegalArgumentException("数量必须大于 0");
        }

        // [手写] 2) 查商品
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在"));

        // [手写] 3) 下架校验
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new IllegalArgumentException("商品已下架，无法加入购物车");
        }

        // [手写] 4) 已存在则累加，不存在则新建
        Optional<CartItem> existing =
                cartItemRepository.findByUserIdAndProductId(userId, productId);

        if (existing.isPresent()) {
            // 已存在 → 数量累加，脏检查自动 UPDATE，不用 save
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + quantity);
        } else {
            // 不存在 → 新建一行
            CartItem item = new CartItem();

            // user 只设 id 就够了，JPA 允许引用已有行的 id，不必先查 User 全量
            User u = new User();
            u.setId(userId);
            item.setUser(u);

            item.setProduct(product);     // product 已经查出来了，直接用
            item.setQuantity(quantity);

            cartItemRepository.save(item);
        }
    }

    /** 查某用户的购物车列表（id 倒序，新加的在前）。 */
    public List<CartItem> getItems(Long userId) {
        // [手写] TODO：一行调用 repository 的查询方法
        // [手写] 直接调 Repository 的 id 倒序查询
        return cartItemRepository.findByUserIdOrderByIdDesc(userId);
    }

    /**
     * 修改数量。约定：quantity <= 0 视作删除该行（计划书验收点）。
     */
    @Transactional
    public void updateQuantity(Long userId, Long cartItemId, int quantity) {

        // [手写] 1) 查项
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new IllegalArgumentException("购物车项不存在"));

        // [手写] 2) 越权校验（Long 必须用 equals，不能用 ==）
        if (!item.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该购物车项");
        }

        // [手写] 3) 数量 <= 0 视作删除；否则改数量（脏检查自动保存）
        if (quantity <= 0) {
            cartItemRepository.delete(item);
            return;
        }
        item.setQuantity(quantity);
    }

    /**
     * 删除单项。
     */
    @Transactional
    public void removeItem(Long userId, Long cartItemId) {

        // [手写] 直接复用 updateQuantity，数量传 0 即视作删除，避免重复写越权校验
        updateQuantity(userId, cartItemId, 0);
    }

    /** 清空购物车。 */
    @Transactional
    public void clear(Long userId) {

        // [手写] 派生删除方法必须在事务里调用，类上方法已经加了 @Transactional
        cartItemRepository.deleteByUserId(userId);
    }

    /**
     * 合计金额 = Σ(商品当前单价 × 数量)，实时计算，不存库。
     */
    public BigDecimal getTotalAmount(Long userId) {


        // [手写] 实时遍历计算，金额不落库
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : getItems(userId)) {
            BigDecimal subtotal = item.getProduct().getPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(subtotal);
        }
        return total;
    }

    /**
     * 购物车商品件数（导航角标用）= Σ数量。
     */
    public long getCartCount(Long userId) {

        // [手写] Stream 写法，一行搞定
        return getItems(userId).stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }
}