// ============================================================
// AI 辅助说明：本文件由 AI 辅助生成/修改
// 生成内容：ProdConfigTest —— 生产 profile 与建表脚本的静态回归测试（P2-4）
// 人工修改：无
// 审查状态：待人工审查
// ============================================================
package com.shopsphere;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产配置静态回归测试（不连数据库，秒级跑完）。
 *
 * <p>对应缺陷清单 P2-4：项目原来只有一份开发配置——模板缓存关着、每条 SQL 打到控制台、
 * 表结构由 {@code ddl-auto=update} 自动改。这份配置部署到服务器上会带来三个问题：
 * 每次请求都重新解析模板、日志被 SQL 刷屏、以及最危险的「实体改了直接 ALTER 生产库」。
 *
 * <p>本测试把 prod profile 的关键开关固化成断言：以后谁改回去，CI 立刻红。
 * 真正的启动验证另做了实测（见测试记录第 62 条）：
 * 空库冷启动建表成功、二次启动幂等、删列后启动被 {@code Schema-validation} 拦下。
 */
class ProdConfigTest {

    /** 读取 classpath 下的 yml 并合并成一个属性源。 */
    private PropertySource<?> loadYaml(String location) throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(location, new ClassPathResource(location));
        assertFalse(sources.isEmpty(), location + " 应能解析出配置");
        return sources.get(0);
    }

    @Test
    @DisplayName("prod profile：模板缓存开、SQL 不回显、日志降噪、ddl-auto=validate")
    void prodProfileTurnsOffDevConveniences() throws Exception {
        PropertySource<?> prod = loadYaml("application-prod.yml");

        assertEquals("true", String.valueOf(prod.getProperty("spring.thymeleaf.cache")),
                "生产必须开模板缓存，否则每个请求都重新解析 HTML");
        assertEquals("validate", String.valueOf(prod.getProperty("spring.jpa.hibernate.ddl-auto")),
                "生产必须用 validate——update 会悄悄 ALTER 生产库");
        assertEquals("false", String.valueOf(prod.getProperty("spring.jpa.show-sql")),
                "生产不能回显 SQL，日志量大且泄露表结构");
        assertEquals("false", String.valueOf(prod.getProperty("spring.jpa.defer-datasource-initialization")),
                "prod 里 SQL 脚本必须在 Hibernate 校验之前跑，否则校验时表还不存在");
        assertEquals("classpath:db/schema.sql",
                String.valueOf(prod.getProperty("spring.sql.init.schema-locations")),
                "建表交给 schema.sql，validate 只负责核对");
        assertEquals("WARN", String.valueOf(prod.getProperty("logging.level.org.hibernate.SQL")),
                "SQL 日志降到 WARN");
        assertEquals("never", String.valueOf(prod.getProperty("server.error.include-stacktrace")),
                "生产不向浏览器吐异常堆栈");
    }

    @Test
    @DisplayName("开发 profile 保持便利：缓存关、SQL 回显、ddl-auto=update")
    void devProfileStaysConvenient() throws Exception {
        PropertySource<?> dev = loadYaml("application.yml");

        assertEquals("false", String.valueOf(dev.getProperty("spring.thymeleaf.cache")),
                "开发改模板要立即生效");
        assertEquals("update", String.valueOf(dev.getProperty("spring.jpa.hibernate.ddl-auto")),
                "开发靠 update 自动建表，省得手写 DDL");
        assertEquals("true", String.valueOf(dev.getProperty("spring.jpa.show-sql")),
                "开发要看 SQL 学 JPQL 翻译结果");
    }

    @Test
    @DisplayName("建表脚本：8 张表齐备且全部 IF NOT EXISTS（可重复执行）")
    void schemaScriptIsCompleteAndIdempotent() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/schema.sql");
        assertTrue(resource.exists(), "db/schema.sql 应随包发布");
        String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

        String[] tables = {"t_user", "category", "product", "cart_item",
                "orders", "order_item", "mail_log", "access_log"};
        for (String table : tables) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table),
                    "schema.sql 应包含 " + table + " 的幂等建表语句");
        }
        // 只数真正的语句行（文件头注释里也出现了这个词，不能用全文 split）
        long creates = sql.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("CREATE TABLE"))
                .count();
        assertEquals(8, creates, "建表语句数量应与实体数一致");
        assertFalse(sql.toLowerCase().contains("drop table"),
                "启动脚本绝不能带 DROP——生产库上执行就是数据丢失");
    }

    @Test
    @DisplayName("docker-compose 用 prod profile 启动应用容器")
    void composeActivatesProdProfile() throws Exception {
        Path compose = Paths.get("docker-compose.yml");
        assertTrue(Files.exists(compose), "仓库根目录应有 docker-compose.yml");
        String yml = Files.readString(compose, StandardCharsets.UTF_8);
        assertTrue(yml.contains("SPRING_PROFILES_ACTIVE: prod"),
                "compose 必须注入 SPRING_PROFILES_ACTIVE=prod，否则容器里跑的还是开发配置");
    }
}
