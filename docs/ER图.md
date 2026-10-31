<!--
AI 辅助说明：本文件由 AI 辅助生成/修改
生成内容：数据库 ER 图（Mermaid 源码）+ 表关系说明
人工修改：无
审查状态：待人工审查
用途：课程报告素材。Mermaid 源码可直接贴入支持 Mermaid 的 Markdown 编辑器渲染，
     或到 https://mermaid.live 粘贴导出 PNG/SVG 图片插入报告
-->

# ShopSphere 数据库 ER 图

```mermaid
erDiagram
    CATEGORY ||--o{ PRODUCT : "1:N 分类下多个商品"
    T_USER ||--o{ CART_ITEM : "1:N 用户的多条购物车行"
    PRODUCT ||--o{ CART_ITEM : "1:N 商品出现在多人购物车"
    T_USER ||--o{ ORDERS : "1:N 用户的多个订单"
    ORDERS ||--|{ ORDER_ITEM : "1:N 订单含多条明细"

    T_USER {
        bigint id PK
        varchar username "唯一"
        varchar password "BCrypt哈希"
        varchar email
        varchar role "CUSTOMER/ADMIN"
        tinyint status
        datetime created_at
    }

    CATEGORY {
        bigint id PK
        varchar name "唯一"
        int sort_order
    }

    PRODUCT {
        bigint id PK
        varchar name
        text description
        decimal price "10,2"
        int stock
        bigint category_id FK
        varchar image_url
        varchar status "ON_SALE/OFF_SALE"
        int sales
        datetime created_at
        datetime updated_at
    }

    CART_ITEM {
        bigint id PK
        bigint user_id FK "UNIQUE(user_id,product_id)"
        bigint product_id FK
        int quantity
        datetime created_at
        datetime updated_at
    }

    ORDERS {
        bigint id PK
        varchar order_no "业务订单号"
        bigint user_id FK
        decimal total_amount "10,2"
        varchar status "CREATED/PAID/SHIPPED/CANCELLED"
        varchar receiver_name "收货信息快照"
        varchar receiver_phone
        varchar receiver_address
        varchar remark
        datetime paid_at
        datetime shipped_at
        datetime cancelled_at
        datetime created_at
        datetime updated_at
    }

    ORDER_ITEM {
        bigint id PK
        bigint order_id FK
        bigint product_id "裸外键(防商品删除连锁)"
        varchar product_name "价格快照"
        decimal product_price "10,2 下单时单价"
        int quantity
        decimal subtotal "10,2"
    }
```

## 关键设计说明

1. **价格快照**：`order_item` 冗余存 `product_name` / `product_price` / `subtotal`，商品后续改价、下架不影响历史订单。
2. **裸外键**：`order_item.product_id` 是 `Long` 而非 `@ManyToOne` 关联——商品被删除时订单明细不受 JPA 级联影响。
3. **唯一约束**：`cart_item` 上 `UNIQUE(user_id, product_id)`，从数据库层保证同一用户同一商品只有一行（加购合并的依据）。
4. **收货快照**：`orders` 冗余存收件人信息，用户后续改资料不影响已下订单。
5. **订单状态机**：`CREATED → PAID → SHIPPED`；`CREATED / PAID → CANCELLED`（取消时回补库存）。
