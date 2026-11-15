// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：WebConfig —— 拦截器注册 + PasswordEncoder Bean（计划书 3.2 AI 范围）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.config;

import com.shopsphere.interceptor.AccessLogInterceptor;
import com.shopsphere.interceptor.AdminInterceptor;
import com.shopsphere.interceptor.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置类：拦截器注册 + 密码加密器。
 *
 * <p>为什么需要 @Configuration？
 * Spring 启动时会扫描所有 @Configuration 类，把里面 @Bean 方法
 * 的返回值注册为容器里的 Bean（单例），供其他类注入使用。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    /** 访问日志拦截器需要注入 Service，所以不能像另外两个那样 new。 */
    private final AccessLogInterceptor accessLogInterceptor;

    /**
     * 注册登录拦截器。
     *
     * <p>addPathPatterns：拦截哪些路径（需要登录才能访问）；
     * excludePathPatterns：放行哪些路径（本例不需要——
     * /login、/register 本来就不在拦截范围内）。
     *
     * <p>拦截的三个路径来自计划书 3.2：
     * /cart/**（购物车）、/order/**（订单）、/checkout/**（结算）。
     * 其中 /cart、/checkout 的 Controller 还没写，先注册不影响运行——
     * 拦截器只对"真实存在的请求路径"生效。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                // [AI辅助 Day5] 追加 /orders/**：我的订单（列表/详情/付款）也必须登录
                .addPathPatterns("/cart/**", "/order/**", "/checkout/**", "/orders/**");

        // [AI辅助 Day6] 后台拦截器：/admin/** 仅 ADMIN 角色可进（内部已含未登录判断，
        // 所以不用再把这些路径塞进 LoginInterceptor）
        registry.addInterceptor(new AdminInterceptor())
                .addPathPatterns("/admin/**");

        // [P0-2] 访问日志：记录 5 个业务动作（浏览列表/商品详情/加购/下单/付款）。
        // 拦 /** 但内部只认白名单路径；静态资源与错误页直接排除，省掉无意义的判断开销。
        registry.addInterceptor(accessLogInterceptor)
                .addPathPatterns("/**")
                // [P0-3] /healthz 也排除：定时探活每 30 分钟一次，别让它污染访问日志
                .excludePathPatterns("/css/**", "/js/**", "/images/**",
                        "/favicon.ico", "/healthz", "/error", "/error/**");
    }

    /**
     * 密码加密器（BCrypt）。
     *
     * <p>为什么自己定义 Bean？
     * 我们只引入了 spring-security-crypto（纯加密工具包），
     * 没引 spring-boot-starter-security（它会自动给全站加登录墙，MVP 不需要），
     * 所以容器里没有现成的 PasswordEncoder，要自己造一个。
     *
     * <p>BCrypt 特性：加盐哈希、同一密码每次加密结果都不同、
     * 校验时用 matches() 而不是字符串比较。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
