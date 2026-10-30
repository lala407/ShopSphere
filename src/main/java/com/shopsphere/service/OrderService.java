// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：OrderService 骨架 —— 事务骨架 + 订单号生成（计划书 3.5 AI 范围，AI 完整实现并讲解）
// 人工修改：createOrder / pay / 查询方法的方法体由 AI 按学员确认补充实现（学员授权直接补全）
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.service;

import com.shopsphere.common.OrderStatus;
import com.shopsphere.common.ProductStatus;
import com.shopsphere.entity.CartItem;
import com.shopsphere.entity.Order;
import com.shopsphere.entity.OrderItem;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.repository.CartItemRepository;
import com.shopsphere.repository.OrderItemRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单业务层：下单（事务）、付款、查询。
 *
 * <p>【事务骨架说明 —— 本日核心知识点】
 * <ol>
 *   <li><b>@Transactional 靠 AOP 代理生效</b>：Spring 给你的 Bean 套了一层代理，
 *       方法被调用时先 begin 事务，正常返回 commit，抛异常 rollback。</li>
 *   <li><b>同类内部调用会失效</b>：同一个类里 this.xxx() 不走代理，
 *       事务注解形同虚设。所以下单相关的写操作都应该在 createOrder 一个方法内完成，
 *       不要在 createOrder 里调 this.helperSaveOrder() 期待它自己开事务。</li>
 *   <li><b>默认只回滚 RuntimeException</b>：受检异常（如 IOException）默认**不回滚**。
 *       所以这里写 rollbackFor = Exception.class，任何异常都回滚。</li>
 *   <li><b>方法必须 public</b>：private/protected 上的 @Transactional 不生效。</li>
 * </ol>
 *
 * <p>【回滚验证思路（步骤 9）】
 * 故意让某商品库存不足 → 第 6 步抛 IllegalArgumentException →
 * 前面已经 save 的 orders / order_item 全部回滚，库里查不到这张订单。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    /**
     * 下单主流程（步骤 2~7 全部在这一个事务里）。
     *
     * <p>⚠️ 务必保持"一个方法内完成"，原因见类注释第 2 条。
     *
     * @param remark 备注，可为 null
     * @return 落库后的订单（此时 order.getId() 已有值）
     * @throws IllegalArgumentException 收货信息不合法 / 购物车为空 / 商品下架 / 库存不足
     */
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(Long userId, String receiverName, String receiverPhone,
                             String receiverAddress, String remark) {

        // 步骤 1：收货信息校验（用户可禁用前端校验直接 POST，后端必须再校验一遍）
        if (receiverName == null || receiverName.isBlank()) {
            throw new IllegalArgumentException("收货人姓名不能为空");
        }
        if (receiverPhone == null || !receiverPhone.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        if (receiverAddress == null || receiverAddress.isBlank()) {
            throw new IllegalArgumentException("收货地址不能为空");
        }

        // 步骤 2：读购物车 + 算总价（顺带校验商品仍在售）
        List<CartItem> cartItems = cartItemRepository.findByUserIdOrderByIdDesc(userId);
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("购物车为空，无法下单");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem ci : cartItems) {
            Product p = ci.getProduct();
            if (p.getStatus() != ProductStatus.ON_SALE) {
                throw new IllegalArgumentException("商品「" + p.getName() + "」已下架，请从购物车移除后再下单");
            }
            total = total.add(p.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())));
        }

        // 步骤 3：组装并保存订单（订单号由 AI 实现的 generateOrderNo 生成）
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        User u = new User();
        u.setId(userId);                // 只设 id，不查全量 User
        order.setUser(u);
        order.setTotalAmount(total);    // 下单时固化
        order.setStatus(OrderStatus.CREATED);
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setReceiverAddress(receiverAddress);
        order.setRemark(remark);
        orderRepository.save(order);    // IDENTITY 主键：save 后 order.getId() 立刻有值

        // 步骤 4：写明细（价格快照：存下单时的商品名与单价）
        for (CartItem ci : cartItems) {
            Product p = ci.getProduct();
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(p.getId());
            item.setProductName(p.getName());
            item.setProductPrice(p.getPrice());
            item.setQuantity(ci.getQuantity());
            item.setSubtotal(p.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())));
            orderItemRepository.save(item);
        }

        // 步骤 5：条件更新扣库存（affected == 0 说明库存不足）
        for (CartItem ci : cartItems) {
            int affected = productRepository.deductStock(ci.getProduct().getId(), ci.getQuantity());
            if (affected == 0) {
                throw new IllegalArgumentException("商品「" + ci.getProduct().getName() + "」库存不足");
            }
        }

        // 步骤 6：清空购物车
        cartItemRepository.deleteByUserId(userId);

        return order;
    }

    /**
     * 模拟付款：CREATED → PAID（计划书 3.5 步骤 8，状态判断属学员范围）。
     *
     * @throws IllegalArgumentException 订单不存在 / 不属于当前用户 / 状态不是待付款
     */
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long userId, Long orderId) {

        // 1) 查订单并校验归属（防越权）
        Order order = getOwnedOrder(userId, orderId);

        // 2) 状态判断：只有 CREATED 能付款
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new IllegalArgumentException("只有待付款订单可以支付");
        }

        // 3) 改状态 + 记录付款时间（@Transactional + 脏检查自动 UPDATE，不用 save）
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
    }

    /** 我的订单列表（按下单时间倒序）。 */
    public List<Order> listByUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 查订单并校验归属（前台所有"我的订单"操作都要先过这一关）。
     *
     * @throws IllegalArgumentException 订单不存在或不属于当前用户
     */
    public Order getOwnedOrder(Long userId, Long orderId) {
        // findByIdAndUserId 一条 SQL 同时做"存在 + 归属"判断，避免越权
        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    }

    /** 某订单的明细列表。 */
    public List<OrderItem> getItems(Long orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }

    /**
     * [AI完整实现] 生成业务单号：ORD + 时间戳 + 4 位随机数，共 21 字符（限长 32）。
     *
     * <p>格式：ORD20251030153012 0042
     * 前缀 ORD 便于人工识别；时间戳前缀让订单自然按时间排序；
     * 4 位随机数降低同秒并发碰撞概率（真高并发应改用雪花算法/号段）。
     *
     * <p>String.format("%04d", n)：不足 4 位补前导零，保证长度固定。
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(10000);
        return "ORD" + timestamp + String.format("%04d", random);
    }
}
