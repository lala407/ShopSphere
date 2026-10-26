// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：Spring Boot 启动类
// 人工修改：本人已确认理解 @SpringBootApplication 注解含义
// 审查状态：已人工审查并修改
// ============================================================
package com.shopsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ShopSphere 启动类
 * <p>
 * {@code @SpringBootApplication} 是一个组合注解，包含三件事：
 * 1. @Configuration        —— 这是一个 Spring 配置类
 * 2. @EnableAutoConfiguration —— 让 Spring Boot 按 classpath 上的依赖自动配置
 * 3. @ComponentScan         —— 自动扫描本包及子包下的 @Component / @Controller / @Service 等
 * <p>
 * 我们所有代码都在 com.shopsphere 及其子包下，所以启动类放在根包即可全覆盖。
 */
@SpringBootApplication
public class ShopSphereApplication {

    // [手写] main 方法是 JVM 入口
    public static void main(String[] args) {
        SpringApplication.run(ShopSphereApplication.class, args);
    }
}
