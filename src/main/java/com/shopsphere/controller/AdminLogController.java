// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：AdminLogController —— 后台访问日志查询页（P0-2）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere.controller;

import com.shopsphere.common.AccessAction;
import com.shopsphere.entity.AccessLog;
import com.shopsphere.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台·访问日志（对应 ex.html「浏览日志 + 购买日志」要求）。
 *
 * <p>三个筛选条件都可选：动作、起始日期、结束日期。
 * 参数用 {@code @DateTimeFormat(iso = DATE)}：HTML 的 {@code <input type="date">}
 * 提交的是 {@code 2026-11-15} 这种格式，不加注解 Spring 没法转成 LocalDate。
 */
@Controller
@RequestMapping("/admin/log")
@RequiredArgsConstructor
public class AdminLogController {

    private final AccessLogService accessLogService;

    /** 每页 20 条：日志表增长快，页面别一次拉太多。 */
    private static final int PAGE_SIZE = 20;

    @GetMapping
    public String list(@RequestParam(required = false) AccessAction action,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {

        Page<AccessLog> pageData = accessLogService.search(action, from, to, page, PAGE_SIZE);

        model.addAttribute("pageData", pageData);
        // 回显筛选条件：翻页时不能把条件丢掉，否则一翻页就变"全部"
        model.addAttribute("action", action);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        // 下拉框选项与四个统计卡
        model.addAttribute("actions", AccessAction.values());
        // 页面用 counts[a.name()] 取值，所以这里转成"枚举名 → 次数"的 Map。
        // 直接塞 EnumMap<AccessAction, Long> 会让 Thymeleaf 的 SpEL 在类型推导上翻车
        // （IllegalArgumentException: The target type java.lang.Enum does not refer to an enum）。
        Map<String, Long> counts = new LinkedHashMap<>();
        accessLogService.countByAction().forEach((a, n) -> counts.put(a.name(), n));
        model.addAttribute("counts", counts);
        model.addAttribute("todayCount", accessLogService.countToday());

        return "admin/log-list";
    }
}
