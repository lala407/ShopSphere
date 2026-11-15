// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AccessLogInterceptor —— 采集浏览 / 购买日志（P0-2）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.interceptor;

import com.shopsphere.common.AccessAction;
import com.shopsphere.service.AccessLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 访问日志拦截器：把「5 个业务动作」翻译成 access_log 里的一行。
 *
 * <p>为什么用拦截器而不是在每个 Controller 里写一行？
 * 记日志是横切关注点，散落在 5 个方法里既容易漏（新加接口忘了写），
 * 又会让业务代码变脏。拦截器集中在一处，规则一目了然。
 *
 * <p>为什么在 {@code afterCompletion} 里记？
 * 它在视图渲染完之后执行，不占用响应时间；同时还能拿到 response 状态码
 * （本例不记状态码，但位置留对了，将来要加 404/500 统计不用改结构）。
 *
 * <p>只匹配"白名单"路径，不做全量记录——见 {@link #resolveAction}。
 */
@Component
@RequiredArgsConstructor
public class AccessLogInterceptor implements HandlerInterceptor {

    private final AccessLogService accessLogService;

    /** /product/12 → 商品详情。 */
    private static final Pattern PRODUCT_DETAIL = Pattern.compile("^/product/(\\d+)$");

    /** /orders/8/pay → 付款。 */
    private static final Pattern ORDER_PAY = Pattern.compile("^/orders/(\\d+)/pay$");

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            // 把"方法 + 路径"映射成动作；不认识的路径返回 null，直接不记
            AccessAction action = resolveAction(request);
            if (action == null) {
                return;
            }
            accessLogService.record(action, targetTypeOf(action), targetIdOf(request, action), request);
        } catch (Exception e) {
            // 双重保险：即使 service 内部漏了异常，也不能让日志问题影响用户请求
        }
    }

    /**
     * 方法 + 路径 → 动作。这是整个功能唯一的"规则表"。
     *
     * <p>刻意不做全量记录：只记浏览和购买两类动作，
     * 后台登录、静态资源、错误页都不进日志，数据干净、查询快。
     */
    private AccessAction resolveAction(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if ("GET".equals(method)) {
            // 首页 = 商品列表页（同一个 Controller 方法）
            if ("/".equals(uri)) {
                return AccessAction.VIEW_LIST;
            }
            if (PRODUCT_DETAIL.matcher(uri).matches()) {
                return AccessAction.VIEW_PRODUCT;
            }
        }

        if ("POST".equals(method)) {
            if ("/cart/add".equals(uri)) {
                return AccessAction.ADD_CART;
            }
            if ("/order/create".equals(uri)) {
                return AccessAction.CREATE_ORDER;
            }
            if (ORDER_PAY.matcher(uri).matches()) {
                return AccessAction.PAY_ORDER;
            }
        }
        return null;
    }

    private String targetTypeOf(AccessAction action) {
        return switch (action) {
            case VIEW_LIST -> "PORTAL";
            case VIEW_PRODUCT -> "PRODUCT";
            case ADD_CART -> "CART";
            case CREATE_ORDER, PAY_ORDER -> "ORDER";
        };
    }

    /** 取出目标 id：商品详情/加购取 productId，付款取路径里的订单 id，其余为 null。 */
    private Long targetIdOf(HttpServletRequest request, AccessAction action) {
        String uri = request.getRequestURI();

        if (action == AccessAction.VIEW_PRODUCT) {
            Matcher m = PRODUCT_DETAIL.matcher(uri);
            return m.matches() ? parseLong(m.group(1)) : null;
        }
        if (action == AccessAction.ADD_CART) {
            return parseLong(request.getParameter("productId"));
        }
        if (action == AccessAction.PAY_ORDER) {
            Matcher m = ORDER_PAY.matcher(uri);
            return m.matches() ? parseLong(m.group(1)) : null;
        }
        // 首页没有具体目标；下单时订单还没生成（id 在响应里），都记 null
        return null;
    }

    private Long parseLong(String raw) {
        try {
            return raw == null ? null : Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
