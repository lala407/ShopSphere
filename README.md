# ShopSphere 在线购物网站

> 《网络应用架构设计与开发》课程实验 
> 学号：202430453150　姓名：周丹璐　班级：网络工程
> 公网演示地址：http://62.234.217.77:8080
> 不能在挂代理器情况下打开网址
> （2026-11-16 复核：首页 / 登录 / 注册 / 商品详情 / `/healthz` 均 200，demo 与 admin 可登录，后台四页正常；静态资源已全部本地化，无外链依赖；登录页底部带「测试账号」提示）

一个可部署上线的单体电商网站最小可行产品：服务器端渲染，覆盖「浏览 → 加购 → 下单 → 付款 → 看订单」完整闭环，配套后台「商品管理 + 订单管理 + 简单统计」。

## 测试账号

| 角色 | 账号 | 密码 | 说明 |
|---|---|---|---|
| 管理员 | `admin` | `admin123` | 种子预置，可进 `/admin` 后台 |
| 普通用户 | `demo` | `demo123` | **预置演示账号**，可直接下单走完整流程；通知邮箱 `3101362993@qq.com` |
| 普通用户 | 自行注册 | — | 注册页即时创建，**邮箱必填**（订单通知发到这里） |

> 验收建议：用 `demo` 登录下单更快，不用先注册；想验「注册 → 登录」链路时再自己注册一个新账号。

## 技术栈

Java 21 · Spring Boot 3.2 · Spring MVC · Thymeleaf · Spring Data JPA · MySQL 8 · Bootstrap 5（**本地文件，不走 CDN**）· BCrypt · Session + 拦截器 · Docker Compose

> **静态资源全部本地化**：`bootstrap.min.css` 与 20 张商品图都放在 `src/main/resources/static/`，
> 页面没有任何外链。断网、校园网限速、CDN 被墙都不影响渲染（早期版本用 jsdelivr + picsum，
> 一旦访问不通整站会变成无样式白页）。

## 功能清单

**顾客端**：注册（**邮箱必填**，用于接收订单通知）/ 登录 / 注销（BCrypt + Session）· 商品列表（分类筛选 + 关键字搜索 + 分页）· 商品详情 · 购物车（加购合并、改量、删除、合计）· 下单（收货校验 → 事务建单 → 价格快照 → 条件扣库存 → 清购物车）· 模拟付款 · 订单历史与详情

**邮件通知**：下单成功 / 付款成功 / 发货 三个节点自动发中文 HTML 邮件 · 事务提交后才发（回滚不发信）· 发信失败不影响订单 · 每封信留痕在 `mail_log`，订单详情页直接显示「已发送至 xxx / 未发送（原因）」

**管理端**（`/admin/**`，仅 ADMIN）：商品 CRUD（软删除上下架）· 订单筛选 / 发货 / 取消（回补库存）· 统计（订单总数、销售额、热销 Top5）· **访问日志**（浏览 / 购买动作，按动作 + 日期筛选，分页）

**日志**：`access_log` 记录 5 个业务动作 —— 浏览列表 / 查看商品（**浏览日志**）、加入购物车 / 提交订单 / 订单付款（**购买日志**），带用户、会话、目标、IP、UA；写失败不影响主流程

**非功能**：登录拦截器 · 管理员拦截器（越权 403）· 访问日志拦截器 · 库存不足整单回滚 · 全局异常处理 + 友好错误页（403/404/500）· 集成测试（H2 内存库，8 个用例）

## 邮件通知配置（SMTP）

下单成功、付款成功、发货三个节点会自动发中文 HTML 邮件。**不配也能跑**：没配 SMTP 账号时，邮件记为 `SKIPPED`，订单照常创建，主流程不受影响。

**QQ 邮箱示例**（163 同理，把 host 换成 `smtp.163.com`）：

1. 登录 QQ 邮箱 → 设置 → 账号 → 开启「POP3/SMTP 服务」；
2. 生成「授权码」（16 位，**不是 QQ 登录密码**）；
3. 配置环境变量：

```bash
# bash / macOS
export MAIL_USERNAME="你的QQ号@qq.com"
export MAIL_PASSWORD="16位授权码"
export APP_BASE_URL="http://localhost:8080"     # 部署后改成公网地址

# PowerShell
$env:MAIL_USERNAME="你的QQ号@qq.com"; $env:MAIL_PASSWORD="16位授权码"
```

Docker 部署时把这三项写进 `.env`（`MAIL_ENABLED / MAIL_HOST / MAIL_PORT / MAIL_USERNAME / MAIL_PASSWORD / APP_BASE_URL`），`docker compose` 会自动注入。

**怎么验证邮件真的发了？**

1. 下单 / 付款 / 发货后，打开订单详情页，会多出一块「邮件通知」：
   - `已发送至 xxx@example.com（时间）` —— 成功；
   - `未发送 —— 未配置发件账号（环境变量 MAIL_USERNAME）` —— 还没配 SMTP；
   - `发送失败 —— MailAuthenticationException: ...` —— 授权码错/被限制。
2. 数据库 `mail_log` 表里每封信一条记录（类型、收件人、结果、失败原因），后台订单详情同样可见。
3. 收件箱里应收到三封：`下单成功` → `付款成功` → `您的订单已发货`。

> 实现要点：邮件事件用 `@TransactionalEventListener(AFTER_COMMIT)` 在**事务提交后**才触发（回滚不发信）；发送失败只记 `FAILED`，绝不回滚订单。`mail_log` 用独立事务（`REQUIRES_NEW`）写入——这是踩过坑的地方，详见 `MailLogWriter` 注释。

## 本地开发（Windows / Mac）

前置：JDK 21、Maven 3.9、本机 MySQL 8（utf8mb4）。

> ⚠️ 项目用 `java.version=21`，**必须让 Maven 用 JDK 21**。若 `mvn -v` 显示 Java 17，会报「无效的目标发行版 21」。
> PowerShell 临时指定：`$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-21..."`（换成你的实际路径）。

```bash
# 1. 克隆
git clone https://github.com/lala407/ShopSphere.git
cd ShopSphere

# 2. 注入数据库密码（PowerShell；bash 用 export）
$env:DB_PASSWORD="你的MySQL密码"

# 3. 启动（首次自动建库建表 + 写入种子数据：4 分类 / 20 商品 / admin 账号）
mvn spring-boot:run
```

更省事的一条命令：`scripts/run-local.ps1` 会自动把 `JAVA_HOME` 指到 JDK 21，并从 `.env` 读取 `DB_PASSWORD` / `MAIL_*` 后再启动；加 `-Check` 只打印当前生效配置（密码打码），不启动。

```powershell
.\scripts\run-local.ps1
.\scripts\run-local.ps1 -Check
```

访问 http://localhost:8080

## Docker 部署（推荐，一条命令）

前置：已安装 Docker 与 Docker Compose（v2+）。

```bash
# 1. 克隆代码
git clone https://github.com/lala407/ShopSphere.git
cd ShopSphere

# 2. 配置密码（复制模板后编辑，.env 不会被提交）
cp .env.example .env
#   Windows: copy .env.example .env
# 把 .env 里的 DB_PASSWORD 改成你的强密码

# 3. 构建并启动（app + mysql 两个容器）
docker compose up -d

# 4. 查看启动日志，出现 Started ShopSphereApplication 即成功
docker compose logs -f app
```

访问 http://服务器IP:8080

> **生产配置说明（P2-4）**：`docker-compose.yml` 已给 app 容器注入 `SPRING_PROFILES_ACTIVE=prod`，
> 生效的是 `src/main/resources/application-prod.yml`——模板缓存开、SQL 不回显、
> 日志降到 INFO、`ddl-auto=validate`（表结构只校验不自动改，建表由 `db/schema.sql` 幂等执行）。
> 本地开发不设该变量，仍用 `application.yml` 的便利配置（缓存关、SQL 回显、`ddl-auto=update`）。

**验证数据持久化**（验收项）：

```bash
docker compose down      # 停掉并删除容器
docker compose up -d     # 重新拉起
# 之前下的订单仍在 —— 数据存在命名卷 mysql-data 里
```

**云服务器额外步骤**（Ubuntu 22.04 示例）：

```bash
# 安装 Docker（国内服务器建议配镜像加速器）
curl -fsSL https://get.docker.com | bash
sudo systemctl enable --now docker

# 安全组 / 防火墙放行 8080 端口（云控制台操作）
# 之后按上面「Docker 部署」1-4 步执行即可
```

无服务器备选：本地 `docker compose up -d` + 内网穿透（cpolar / ngrok）暴露 8080。

## 运行期可用性保障（3 个月可访问）

验收要求「部署后 3 个月内可访问」，这件事拆成三层，每层都有对应的落地手段：

| 层次 | 手段 | 说明 |
|---|---|---|
| 进程挂了自动拉起 | `docker-compose.yml` 里 app / mysql 均 `restart: unless-stopped` | 容器异常退出或服务器重启后自动恢复，无需人工干预 |
| 数据不丢 | 命名卷 `mysql-data` | `docker compose down` 甚至重建容器，订单仍在 |
| **故障可发现** | `GET /healthz` + GitHub Actions 每 30 分钟探活 | 见下 |

### 探活端点 `/healthz`

```bash
curl -i http://62.234.217.77:8080/healthz
# HTTP/1.1 200
# UP db=9ms
```

它不只是「Tomcat 还活着」——内部执行一次 `SELECT 1` 打到 MySQL，
所以**应用与数据库任何一个挂掉都会返回 503**（`DOWN db=unavailable`），
便于监控脚本直接按状态码判断，不用解析响应体。该端点不返回任何业务数据，
已在 `WebConfig` 里排除访问日志拦截器，不会污染 `access_log`。

### 云端定时探活

`.github/workflows/uptime.yml`：每 30 分钟（UTC `*/30 * * * *`）从 GitHub 的机器上
`curl` 一次公网 `/healthz`，连续 3 次失败（间隔 20 秒重试）才判死并让工作流红灯 ——
GitHub 会把失败结果邮件通知仓库所有者，等于「挂了能收到通知」，且不依赖本人电脑开机。

> 两个已知限制：① GitHub 的定时任务在免费仓库连续 60 天无提交后会自动暂停，
> 需要在 Actions 页面点一次「Enable workflow」，或改用 UptimeRobot 之类的免费监控（同样打 `/healthz`）；
> ② 定时任务在高峰期可能延迟数分钟，探活不需要精确，可接受。

## 项目结构

```
src/main/java/com/shopsphere/
├── config/          # WebConfig（拦截器注册）、全局异常处理、导航购物车角标
├── interceptor/     # 登录拦截器、管理员拦截器、访问日志拦截器
├── controller/      # 用户/商品/购物车/订单 + 后台管理（Admin*）
├── service/         # 业务层（下单事务、库存扣减、邮件发送 MailService / MailLogWriter、访问日志 AccessLogService）
├── listener/        # OrderMailListener：事务提交后触发发信
├── event/           # OrderMailEvent：邮件事件载体（record）
├── repository/      # Spring Data JPA 仓库 + 热销统计投影
├── entity/          # User / Category / Product / CartItem / Order / OrderItem / MailLog / AccessLog
└── common/          # 订单/商品状态、角色、邮件类型/状态、访问动作枚举
src/main/resources/
├── application.yml  # 数据源 + SMTP 均支持环境变量注入
├── data.sql         # 幂等种子数据（INSERT IGNORE，每次启动自动执行）
├── static/          # 本地静态资源：css/bootstrap.min.css（5.3.3 原样）、images/p01–p20.svg + favicon
└── templates/       # Thymeleaf 页面（前台 + admin + error）
src/test/
├── java/.../OrderMailFlowTest.java   # 下单→付款→发货 全链路 + 邮件日志（MockMvc + H2）
├── java/.../AccessLogFlowTest.java   # 浏览/购买日志采集 + 后台日志页渲染
├── java/.../MailServiceTest.java     # 邮件拼装/发送成功/发送失败
└── resources/application-test.yml    # H2 内存库配置
Dockerfile           # 多阶段构建（Maven 构建 → JRE 运行，-Xmx256m）
docker-compose.yml   # app + mysql + 数据卷编排
```

## 测试

```bash
mvn test        # 8 个用例，跑在 H2 内存库上，不依赖本机 MySQL、不发真实邮件
```

覆盖：注册邮箱校验、下单→付款→发货主链路、每步写一条 `mail_log`、订单详情页渲染、邮件发送成功（SENT）与 SMTP 异常（FAILED）两种结果。

## 数据库设计

8 张表：`t_user`（用户）、`category`（分类）、`product`（商品）、`cart_item`（购物车，`UNIQUE(user_id, product_id)` 保证同商品一行）、`orders`（订单，收货快照 + 状态机）、`order_item`（订单明细，**商品名与单价快照**，不随商品改价变化）、`mail_log`（邮件发送日志：类型 / 收件人 / SENT·FAILED·SKIPPED / 失败原因）、`access_log`（浏览 / 购买日志：动作、用户、会话、目标、IP、UA，索引 `created_at` / `action` / `session_id`）。

关系：`product → category` 多对一；`cart_item / orders → t_user` 多对一；`order_item → orders / product` 多对一；`mail_log.order_id` 逻辑关联订单（**不建外键**——审计日志不该参与订单实体加载）。

## 部署排错清单

| 症状 | 排查 |
|---|---|
| `docker compose up` 拉镜像超时 | 配 Docker 镜像加速器后重试 |
| app 反复重启、`exit code 137` | OOM：确认 Dockerfile 的 `-Xmx256m` 生效；MySQL buffer 已调至 128M |
| app 起不来、日志连不上数据库 | `docker compose logs mysql` 确认 mysql 健康；确认 `.env` 存在且 DB_PASSWORD 无误 |
| 页面 500 / 改了不生效 | 看 `docker compose logs -f app` 栈顶第一行 Caused by |
| 公网访问不通 | 云控制台安全组放行 8080；`curl http://localhost:8080` 先在服务器本机验证 |
| 重启后数据丢了 | 检查是否用了 `docker compose down -v`（`-v` 会删卷！）；正常 `down` 不删数据 |

## AI 辅助说明（学术诚信声明）

本项目按课程《项目计划书》0.3 节分工原则开发：学员手写 ≥40% 代码（实体、简单查询、注册登录、购物车、简单页面等），AI 完整实现最难的骨架与算法部分（脚手架、多条件分页、下单事务、库存扣减、统计 SQL、全局异常处理、Docker 部署、复杂后台页面）。

标注证据链（三份互相印证）：

1. **源码文件头**：所有 AI 参与文件均含「AI 辅助说明」注释块（生成内容 / 人工修改 / 审查状态）；
2. **Git 提交前缀**：`AI辅助：` 与 `手写：` 严格区分提交粒度；
3. **本节汇总**：AI 完整实现的文件清单 —— `pom.xml`、`application.yml`、`data.sql`、`WebConfig`、全局异常处理、拦截器骨架、多条件分页查询、`OrderService.createOrder` 事务骨架、库存条件更新、热销统计投影、后台 5 个页面、错误页 ×3、`Dockerfile`、`docker-compose.yml`、本 README 框架。

## 报告素材

- ER 图、功能截图、测试记录：见 `docs/` 目录
- 项目计划与每日验收：`docs/项目计划书.md`
