// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：HelloController 骨架（用于验证 Spring Boot 跑通）
// 人工修改：本人手写 getHello() 方法体
// 审查状态：已人工审查并修改
// ============================================================
package com.shopsphere.controller;

// [AI辅助] 修复：类上改用 @RestController 后，import 同步换成 RestController（原来还是 Controller，编译不过）
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 最简单的 Controller —— 用来验证 Spring Boot 空壳能跑。
 * <p>
 * 跑通标准：浏览器访问 http://localhost:8080/hello 能看到 "Hello, ShopSphere!" 文字。
 * <p>
 * 注解说明：
 * - @RestController = @Controller + @ResponseBody 的合体注解：
 *   类里所有方法的返回值都直接写进 HTTP 响应体，不找模板渲染
 *   （后面做 Thymeleaf 页面时，那些页面 Controller 要改用 @Controller）
 */
@RestController
public class HelloController {

    // [AI骨架] 方法签名由 AI 提供，方法体由本人手写
    @GetMapping("/hello")
    public String getHello() {
        return "Hello, ShopSphere!  项目脚手架跑通了 🎉";
    }
}
