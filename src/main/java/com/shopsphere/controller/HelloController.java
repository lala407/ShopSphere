// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：HelloController 骨架（用于验证 Spring Boot 跑通）
// 人工修改：本人手写 getHello() 方法体
// 审查状态：已人工审查并修改
// ============================================================
package com.shopsphere.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 最简单的 Controller —— 用来验证 Spring Boot 空壳能跑。
 * <p>
 * 跑通标准：浏览器访问 http://localhost:8080/hello 能看到 "Hello, ShopSphere!" 文字。
 * <p>
 * 注解说明：
 * - @Controller     告诉 Spring 这是一个处理 HTTP 请求的类
 * - @ResponseBody   方法返回值直接写进 HTTP 响应体，不找模板渲染
 *   （后面做 Thymeleaf 页面时会去掉这个注解，改用返回模板名）
 */
@Controller
public class HelloController {

    // [AI骨架] 方法签名由 AI 提供，方法体由本人手写
    @GetMapping("/hello")
    @ResponseBody
    public String getHello() {
        return "Hello, ShopSphere!  项目脚手架跑通了 🎉";
    }
}
