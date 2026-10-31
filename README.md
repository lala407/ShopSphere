<!--
============================================================
AI 辅助说明：本文件由 AI 辅助生成/修改
生成内容：README 完整版 —— 项目介绍、本地/Docker 部署步骤、测试账号、排错清单、AI 辅助说明章节
人工修改：学号姓名等个人信息、部署后的公网地址需学员自行填写
审查状态：待人工审查
============================================================
-->

# ShopSphere 在线购物网站

> 《网络应用架构设计与开发》课程实验 · 7 天 MVP
> 学号：______　姓名：______　班级：______
> 公网演示地址：______（部署后填写）

一个可部署上线的单体电商网站最小可行产品：服务器端渲染，覆盖「浏览 → 加购 → 下单 → 付款 → 看订单」完整闭环，配套后台「商品管理 + 订单管理 + 简单统计」。

## 技术栈

Java 21 · Spring Boot 3.2 · Spring MVC · Thymeleaf · Spring Data JPA · MySQL 8 · Bootstrap 5 · BCrypt · Session + 拦截器 · Docker Compose

## 功能清单

**顾客端**：注册 / 登录 / 注销（BCrypt + Session）· 商品列表（分类筛选 + 关键字搜索 + 分页）· 商品详情 · 购物车（加购合并、改量、删除、合计）· 下单（收货校验 → 事务建单 → 价格快照 → 条件扣库存 → 清购物车）· 模拟付款 · 订单历史与详情

**管理端**（`/admin/**`，仅 ADMIN）：商品 CRUD（软删除上下架）· 订单筛选 / 发货 / 取消（回补库存）· 统计（订单总数、销售额、热销 Top5）

**非功能**：登录拦截器 · 管理员拦截器（越权 403）· 库存不足整单回滚 · 全局异常处理 + 友好错误页（403/404/500）

## 测试账号

| 角色 | 账号 | 密码 | 说明 |
|---|---|---|---|
| 管理员 | `admin` | `admin123` | 种子预置，可进 `/admin` 后台 |
| 普通用户 | 自行注册 | — | 注册页即时创建 |

## 本地开发（Windows / Mac）

前置：JDK 21、Maven 3.9、本机 MySQL 8（utf8mb4）。

```bash
# 1. 克隆
git clone https://github.com/lala407/ShopSphere.git
cd ShopSphere

# 2. 注入数据库密码（PowerShell；bash 用 export）
$env:DB_PASSWORD="你的MySQL密码"

# 3. 启动（首次自动建库建表 + 写入种子数据：4 分类 / 20 商品 / admin 账号）
mvn spring-boot:run
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

## 项目结构

```
src/main/java/com/shopsphere/
├── config/          # WebConfig（拦截器注册）、全局异常处理、导航购物车角标
├── interceptor/     # 登录拦截器、管理员拦截器
├── controller/      # 用户/商品/购物车/订单 + 后台管理（Admin*）
├── service/         # 业务层（下单事务、库存扣减）
├── repository/      # Spring Data JPA 仓库 + 热销统计投影
├── entity/          # User / Category / Product / CartItem / Order / OrderItem
└── common/          # 订单/商品状态枚举、角色常量
src/main/resources/
├── application.yml  # 数据源支持环境变量注入（DB_URL/DB_USERNAME/DB_PASSWORD）
├── data.sql         # 幂等种子数据（INSERT IGNORE，每次启动自动执行）
└── templates/       # Thymeleaf 页面（前台 + admin + error）
Dockerfile           # 多阶段构建（Maven 构建 → JRE 运行，-Xmx256m）
docker-compose.yml   # app + mysql + 数据卷编排
```

## 数据库设计

6 张表：`t_user`（用户）、`category`（分类）、`product`（商品）、`cart_item`（购物车，`UNIQUE(user_id, product_id)` 保证同商品一行）、`orders`（订单，收货快照 + 状态机）、`order_item`（订单明细，**商品名与单价快照**，不随商品改价变化）。

关系：`product → category` 多对一；`cart_item / orders → t_user` 多对一；`order_item → orders / product` 多对一。

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

本项目按课程《项目计划书》0.3 节分工原则开发：学员手写 ≥60% 代码（实体、简单查询、注册登录、购物车、简单页面等），AI 完整实现最难的骨架与算法部分（脚手架、多条件分页、下单事务、库存扣减、统计 SQL、全局异常处理、Docker 部署、复杂后台页面）。

标注证据链（三份互相印证）：

1. **源码文件头**：所有 AI 参与文件均含「AI 辅助说明」注释块（生成内容 / 人工修改 / 审查状态）；
2. **Git 提交前缀**：`AI辅助：` 与 `手写：` 严格区分提交粒度；
3. **本节汇总**：AI 完整实现的文件清单 —— `pom.xml`、`application.yml`、`data.sql`、`WebConfig`、全局异常处理、拦截器骨架、多条件分页查询、`OrderService.createOrder` 事务骨架、库存条件更新、热销统计投影、后台 5 个页面、错误页 ×3、`Dockerfile`、`docker-compose.yml`、本 README 框架。

## 报告素材

- ER 图、功能截图、测试记录：见 `docs/` 目录
- 项目计划与每日验收：`docs/项目计划书.md`
