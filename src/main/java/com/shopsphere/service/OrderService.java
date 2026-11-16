// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：OrderService 骨架 —— 事务骨架 + 订单号生成（计划书 3.5 AI 范围，AI 完整实现并讲解）
//          [Day6] 后台订单状态流转：ship 发货、cancel 取消（回补库存）
// 人工修改：createOrder / pay / 查询方法的方法体由 AI 按学员确认补充实现（学员授权直接补全）
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.service;

import com.shopsphere.common.OrderStatus;
import com.shopsphere.common.ProductStatus;
import com.shopsphere.common.MailType;
import com.shopsphere.entity.CartItem;
import com.shopsphere.entity.Order;
import com.shopsphere.entity.OrderItem;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.event.OrderMailEvent;
import com.shopsphere.repository.CartItemRepository;
import com.shopsphere.repository.OrderItemRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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

    /** [P2-7] 订单号时间戳格式：精确到毫秒（17 位）。 */
    private static final DateTimeFormatter ORDER_NO_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    /** [P2-7] 订单号查重重试次数。 */
    private static final int ORDER_NO_MAX_RETRY = 5;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    /**
     * [AI辅助 Day8] 下单要发确认邮件，需要拿到用户邮箱，所以这里查一次 User。
     * 之前为了省一次查询只 new 一个带 id 的 User，现在有邮件需求，省不得了。
     */
    private final UserRepository userRepository;

    /**
     * 事件发布器：下单/付款/发货成功后发布 OrderMailEvent，
     * 由 OrderMailListener 在**事务提交之后**发邮件（详见该监听器注释）。
     */
    private final ApplicationEventPublisher eventPublisher;

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

        // 步骤 3：组装并保存订单（订单号由 nextUniqueOrderNo 生成：毫秒时间戳 + 随机位 + 查重重试）
        Order order = new Order();
        order.setOrderNo(nextUniqueOrderNo());

        // [AI辅助 Day8] 查真实 User：邮件要发给 user.email。
        // 先把 email 取到局部变量——后面 deductStock 的 clearAutomatically 会清空持久化上下文，
        // 届时 user 已脱管，虽然字段还能读，但"提前取出来"语义更清晰、也避免任何懒加载意外。
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在，请重新登录"));
        String userEmail = user.getEmail();

        order.setUser(user);
        order.setTotalAmount(total);    // 下单时固化
        order.setStatus(OrderStatus.CREATED);
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setReceiverAddress(receiverAddress);
        order.setRemark(remark);
        orderRepository.save(order);    // IDENTITY 主键：save 后 order.getId() 立刻有值

        // 步骤 4：写明细（价格快照：存下单时的商品名与单价）
        // 顺手拼一份"商品 ×数量"摘要，给确认邮件正文用
        StringBuilder summary = new StringBuilder();
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

            if (summary.length() > 0) {
                summary.append("；");
            }
            summary.append(p.getName()).append(" ×").append(ci.getQuantity());
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

        // 步骤 7：[AI辅助 Day8] 发布"下单成功"邮件事件。
        // ⚠️ 这里只是"举手说一声"，真正发信发生在**本方法的事务提交之后**
        //    （OrderMailListener 标注了 @TransactionalEventListener(AFTER_COMMIT)）。
        //    如果把 mailService.send 直接写在这里，后面任何一步抛异常回滚，
        //    顾客就会收到一封"下单成功"、库里却没有订单的邮件。
        eventPublisher.publishEvent(new OrderMailEvent(
                order.getId(), order.getOrderNo(), userEmail, receiverName,
                total, summary.toString(), MailType.ORDER_CREATED, LocalDateTime.now()));

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

        // 4) [AI辅助 P1-2 修复] 付款成功累加销量。
        //    口径：前台「已售 N 件」= 已付款成交量，与统计页热销 Top5 一致；
        //    放在 pay() 里而不是下单时，是因为待付款订单可能被取消，下单口径会虚高。
        //    状态改成功 + 销量累加同在 pay 的这一个事务里：要么一起提交，要么一起回滚。
        for (OrderItem item : orderItemRepository.findByOrderId(orderId)) {
            productRepository.incrementSales(item.getProductId(), item.getQuantity());
        }

        // 5) [AI辅助 Day8] 发布"付款成功"邮件事件（提交后再发，见类顶部说明）
        publishMailEvent(order, MailType.ORDER_PAID);
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

    // ==========================================================
    // [AI完整实现 Day6] 后台订单管理：发货 / 取消
    // （计划书 0.3 分工：订单状态流转属 AI 范围）
    // ==========================================================

    /**
     * 发货：PAID → SHIPPED。
     *
     * <p>和前台的 pay() 同一套路：查订单 → 状态判断 → 改状态 + 记时间，
     * 靠 @Transactional 的脏检查自动 UPDATE，不用调 save。
     * 区别只在"谁触发的"：pay 是用户点付款（要校验归属），ship 是管理员发货（不用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalArgumentException("只有已付款订单可以发货");
        }
        order.setStatus(OrderStatus.SHIPPED);
        order.setShippedAt(LocalDateTime.now());

        // [AI辅助 Day8] 发货通知邮件：ex.html 的"发送电子邮件确认发货"就落在这一步
        publishMailEvent(order, MailType.ORDER_SHIPPED);
    }

    /**
     * 取消订单（后台）：CREATED / PAID → CANCELLED，并回补库存。
     *
     * <p>为什么两种状态取消都要回补库存？
     * 本项目的库存是在【下单那一刻】就扣掉的（见 createOrder 步骤 5），
     * 不是付款时才扣。所以无论订单停在 CREATED 还是 PAID，
     * 取消时都必须把数量加回去，否则这批货就"凭空消失"了。
     *
     * <p>SHIPPED 之后不允许取消——货已发出，现实中要走退货流程（MVP 不做）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId) {
        // [AI辅助 P1-3] 状态流转逻辑抽到 doCancel，后台 / 用户取消共用（见下）
        doCancel(orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在")));
    }

    /**
     * [AI辅助 P1-3] 用户取消自己的待付款订单：仅 CREATED → CANCELLED，回补库存。
     *
     * <p>为什么用户不能取消已付款订单？——钱已经付了，现实里要走退款流程，
     * MVP 没有支付渠道对接，退不了钱，所以已付款订单的取消收口到后台管理员
     * （cancel(orderId)），用户侧只放开"还没付钱"的 CREATED。
     *
     * <p>⚠️ doCancel 是同类私有方法、不走代理——事务由本方法（public + @Transactional）
     * 开启，内部调用在同一个事务里执行，符合类注释第 2 条的用法。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelByUser(Long userId, Long orderId) {
        Order order = getOwnedOrder(userId, orderId);   // 归属校验：只能取消自己的
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new IllegalArgumentException("只有待付款订单可以自行取消，已付款订单请联系客服");
        }
        doCancel(order);
    }

    /** 取消的公共实现（后台/用户两路共用）：回补库存 +（已付款时）回退销量。 */
    private void doCancel(Order order) {
        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PAID) {
            throw new IllegalArgumentException("当前状态不允许取消（仅待付款/已付款订单可取消）");
        }
        // [AI辅助 P1-2 修复] 先记下取消前的状态：销量只在付款时累加过，
        // 所以只有取消"已付款"订单才需要把销量减回去；待付款订单从没加过，不动。
        boolean wasPaid = order.getStatus() == OrderStatus.PAID;

        // 逐条明细回补库存（无条件 UPDATE，见 ProductRepository.restoreStock）
        for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
            productRepository.restoreStock(item.getProductId(), item.getQuantity());
            if (wasPaid) {
                // 与付款时的 incrementSales 对称回退（CASE WHEN 兜底防负数）
                productRepository.decrementSales(item.getProductId(), item.getQuantity());
            }
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());

        // ⚠️ 必须显式 save：restoreStock 的 clearAutomatically=true 会清空持久化上下文，
        // order 实体已被"脱管"（detached），靠脏检查自动 UPDATE 的前提不存在了。
        // save() 触发 merge，把脱管实体的状态合并回上下文，提交时才发出 UPDATE。
        orderRepository.save(order);
    }

    /**
     * [AI辅助 P1-3] 确认收货（后台代操作）：SHIPPED → COMPLETED，写 completed_at。
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long orderId) {
        doConfirm(orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在")));
    }

    /**
     * [AI辅助 P1-3] 用户确认收货：SHIPPED → COMPLETED，写 completed_at。
     *
     * <p>这是状态机 COMPLETED 状态的**唯一入口**（修复前 COMPLETED 没有任何流转可达，
     * "查看订单状态"只能看到徽章、没有闭环）。收货是买家的动作，所以走归属校验。
     *
     * <p>不发确认收货邮件：MailType 只定义了下单/付款/发货三种，收货是
     * "买家自己点的"，再发一封通知意义不大，MVP 不扩。
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirmByUser(Long userId, Long orderId) {
        doConfirm(getOwnedOrder(userId, orderId));      // 归属校验：只能确认自己的
    }

    /** 确认收货的公共实现（后台/用户两路共用）。 */
    private void doConfirm(Order order) {
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new IllegalArgumentException("只有已发货订单可以确认收货");
        }
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        // 不调 @Modifying 查询、持久化上下文没被清，脏检查自动 UPDATE，不用 save
    }

    /**
     * [AI辅助 Day8] 发布订单邮件事件（付款 / 发货共用）。
     *
     * <p>为什么不直接调 MailService？
     * 见 {@link OrderMailEvent} 的注释：事务提交后才发、发信失败不影响主流程、业务层不依赖 SMTP。
     *
     * <p>商品摘要从 order_item 查（下单/付款/发货三个时点都能用同一套逻辑），
     * 明细行数少，多这一次查询换来代码复用是划算的。
     */
    private void publishMailEvent(Order order, MailType type) {
        String email = order.getUser() == null ? null : order.getUser().getEmail();
        String itemSummary = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(i -> i.getProductName() + " ×" + i.getQuantity())
                .collect(Collectors.joining("；"));

        eventPublisher.publishEvent(new OrderMailEvent(
                order.getId(),
                order.getOrderNo(),
                email,
                order.getReceiverName(),
                order.getTotalAmount(),
                itemSummary,
                type,
                LocalDateTime.now()));
    }

    /**
     * [P2-7] 生成业务单号并做查重重试。
     *
     * <p>原实现是「秒级时间戳 + 4 位随机数」：同一秒内 10000 个随机值，
     * 量级一上来（或压测脚本并发下单）就会撞上 {@code uk_order_no} 唯一键，
     * 抛 DataIntegrityViolationException 直接变成 500——用户看到的是「下单失败」，
     * 而且事务已回滚，体验和数据都受损。
     *
     * <p>现在两道保险：
     * <ol>
     *   <li>时间戳精确到毫秒 + 6 位随机数，同毫秒碰撞概率 1/1000000；</li>
     *   <li>生成后先查库，撞了就重生成（最多 5 次）——把极小概率的冲突消化在应用层，
     *       而不是让它变成唯一键异常。</li>
     * </ol>
     * 仍冲突说明随机源出问题，抛异常比悄悄覆盖更安全（由全局异常处理兜底）。
     */
    private String nextUniqueOrderNo() {
        for (int i = 0; i < ORDER_NO_MAX_RETRY; i++) {
            String candidate = generateOrderNo();
            if (!orderRepository.existsByOrderNo(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("订单号生成冲突，请重新提交订单");
    }

    /**
     * 生成业务单号：ORD + 17 位毫秒时间戳 + 6 位随机数，共 26 字符（列宽 32）。
     *
     * <p>格式：ORD20251030153012345 004269
     * 前缀 ORD 便于人工识别；时间戳前缀让订单自然按时间排序；
     * 毫秒 + 6 位随机数把同刻碰撞概率压到百万分之一（配合上面的查重重试基本不会失败）。
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(ORDER_NO_TIMESTAMP);
        int random = ThreadLocalRandom.current().nextInt(1_000_000);
        return "ORD" + timestamp + String.format("%06d", random);
    }
}
