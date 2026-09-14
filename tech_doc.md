# 动力伞飞行基地地面固定锚点航线气流区间承重适配系统

## 一、项目概述

### 1.1 项目背景
本系统是针对动力伞飞行基地的地面固定锚点管理系统，核心功能是实现航线气流区间与地面锚点承重的智能适配校验，确保飞行安全。

### 1.2 核心业务逻辑
- 地面固定锚点基础建档：编号、最大承重、适配气流区间
- 动力伞航线绑定锚点，自动校验气流承重适配范围
- 航线气流参数更新，重新匹配适配锚点并留存记录
- 按气流区间筛选对应承重锚点清单

### 1.3 项目定位
中等难度，动力伞飞行小众场景，独有航线气流承重适配校验逻辑。

---

## 二、技术架构

### 2.1 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 前端 | Vue | 3.x |
| 前端构建 | Vite | 6.x |
| 前端UI | Element Plus | 2.x |
| 前端语言 | TypeScript | 5.x |
| 样式 | Tailwind CSS | 3.x |
| 后端框架 | Spring Boot | 3.3.x |
| 后端语言 | Java | 17 |
| ORM | Spring Data JPA | 3.3.x |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7.x |
| 容器化 | Docker / Docker Compose | 最新 |

### 2.2 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Vue3)                              │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ 锚点管理页面  │  │ 航线管理页面  │  │ 适配校验页面           │ │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬─────────────┘ │
└─────────┼─────────────────┼──────────────────────┼──────────────┘
          │                 │                      │
          ▼                 ▼                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                      后端 (Spring Boot)                         │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ AnchorController│ │ RouteController│ │ AdaptController      │ │
│  │ 锚点API      │  │ 航线API      │  │ 适配校验API           │ │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬─────────────┘ │
│         │                 │                      │               │
│  ┌──────▼───────┐  ┌──────▼───────┐  ┌──────────▼─────────────┐ │
│  │ AnchorService │ │ RouteService │ │ AdaptService           │ │
│  │ 锚点业务逻辑  │ │ 航线业务逻辑  │ │ 适配校验核心逻辑       │ │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬─────────────┘ │
└─────────┼─────────────────┼──────────────────────┼──────────────┘
          │                 │                      │
          ▼                 ▼                      ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│     MySQL        │  │     Redis         │  │   数据卷挂载     │
│   (3373端口)     │  │   (6446端口)      │  │  适配流水记录    │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

---

## 三、核心业务模块设计

### 3.1 模块划分

| 模块 | 功能 | 说明 |
|------|------|------|
| 锚点管理 | 地面固定锚点基础建档、编辑、删除、查询 | 编号、最大承重、适配气流区间 |
| 航线管理 | 动力伞航线管理、绑定锚点、气流参数更新 | 航线分组、气流区间联动 |
| 适配校验 | 自动校验气流承重适配范围、重新匹配、记录流水 | 独有核心逻辑 |
| 筛选查询 | 按气流区间筛选对应承重锚点清单 | 联动筛选控件 |

### 3.2 核心校验逻辑

#### 3.2.1 气流承重适配规则

```
锚点适配条件：锚点最大承重 >= 航线气流强度要求

气流强度区间划分：
┌─────────────────────────────────────────────────────────────┐
│ 气流等级 │ 气流强度区间 (m/s) │ 最低承重要求 (kg) │ 适用场景       │
├──────────┼────────────────────┼───────────────────┼───────────────┤
│ 微风     │ 0 - 3             │ 500               │ 训练航线       │
│ 轻风     │ 3 - 6             │ 800               │ 常规航线       │
│ 和风     │ 6 - 10            │ 1200              │ 进阶航线       │
│ 强风     │ 10 - 15           │ 1800              │ 专业航线       │
│ 疾风     │ 15 - 20           │ 2500              │ 极限航线       │
└──────────┴────────────────────┴───────────────────┴───────────────┘
```

#### 3.2.2 适配校验流程

```
航线绑定锚点时：
1. 获取航线的气流强度参数
2. 查询锚点的最大承重和适配气流区间
3. 校验：锚点适配气流上限 >= 航线气流强度
4. 校验通过：绑定成功
5. 校验失败：返回错误信息，禁止绑定

航线气流参数更新时：
1. 获取更新后的气流强度参数
2. 查询该航线已绑定的所有锚点
3. 逐一校验锚点适配范围
4. 不适配的锚点：记录变更流水，解除绑定
5. 适配的锚点：保持绑定状态
```

---

## 四、数据库设计

### 4.1 数据库表结构

#### 4.1.1 anchor（地面固定锚点表）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | 主键ID |
| anchor_code | VARCHAR(50) | UNIQUE, NOT NULL | 锚点编号 |
| max_weight | DECIMAL(10,2) | NOT NULL | 最大承重 (kg) |
| min_wind_speed | DECIMAL(5,2) | NOT NULL | 适配气流下限 (m/s) |
| max_wind_speed | DECIMAL(5,2) | NOT NULL | 适配气流上限 (m/s) |
| location_desc | VARCHAR(200) | | 位置描述 |
| status | TINYINT | DEFAULT 1 | 状态：0-停用，1-启用 |
| create_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

#### 4.1.2 flight_route（动力伞航线表）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | 主键ID |
| route_code | VARCHAR(50) | UNIQUE, NOT NULL | 航线编号 |
| route_name | VARCHAR(100) | NOT NULL | 航线名称 |
| route_group | VARCHAR(50) | NOT NULL | 航线分组 |
| wind_speed | DECIMAL(5,2) | NOT NULL | 当前气流强度 (m/s) |
| wind_level | VARCHAR(20) | | 气流等级（微风/轻风/和风/强风/疾风） |
| description | VARCHAR(500) | | 航线描述 |
| status | TINYINT | DEFAULT 1 | 状态：0-停用，1-启用 |
| create_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

#### 4.1.3 route_anchor（航线锚点绑定表）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | 主键ID |
| route_id | BIGINT | FOREIGN KEY, NOT NULL | 航线ID |
| anchor_id | BIGINT | FOREIGN KEY, NOT NULL | 锚点ID |
| bind_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 绑定时间 |
| unbind_time | DATETIME | | 解绑时间 |
| status | TINYINT | DEFAULT 1 | 状态：0-解绑，1-绑定 |
| create_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

#### 4.1.4 adapt_log（适配调整流水表）

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | 主键ID |
| route_id | BIGINT | NOT NULL | 航线ID |
| route_code | VARCHAR(50) | NOT NULL | 航线编号 |
| anchor_id | BIGINT | NOT NULL | 锚点ID |
| anchor_code | VARCHAR(50) | NOT NULL | 锚点编号 |
| operation_type | VARCHAR(20) | NOT NULL | 操作类型：BIND/UNBIND/REBIND |
| before_wind_speed | DECIMAL(5,2) | | 操作前气流强度 |
| after_wind_speed | DECIMAL(5,2) | | 操作后气流强度 |
| before_weight | DECIMAL(10,2) | | 操作前锚点承重 |
| after_weight | DECIMAL(10,2) | | 操作后锚点承重 |
| reason | VARCHAR(500) | | 操作原因 |
| operator | VARCHAR(50) | | 操作人 |
| create_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

### 4.2 ER关系图

```
anchor (1) ──── (*) route_anchor ──── (*) flight_route (1)
    │                                          │
    │                                          │
    └──────────────────────────────────────────┘
                          │
                          ▼
                    adapt_log
```

---

## 五、API接口设计

### 5.1 锚点管理接口

| API路径 | HTTP方法 | Controller | 功能描述 |
|---------|----------|------------|----------|
| /api/anchor | POST | AnchorController | 创建锚点 |
| /api/anchor | GET | AnchorController | 查询锚点列表 |
| /api/anchor/{id} | GET | AnchorController | 查询锚点详情 |
| /api/anchor/{id} | PUT | AnchorController | 更新锚点 |
| /api/anchor/{id} | DELETE | AnchorController | 删除锚点 |
| /api/anchor/filter | GET | AnchorController | 按气流区间筛选锚点 |

#### 请求/响应示例

**POST /api/anchor**

请求体：
```json
{
    "anchorCode": "ANCHOR-001",
    "maxWeight": 1200.00,
    "minWindSpeed": 0.00,
    "maxWindSpeed": 10.00,
    "locationDesc": "基地A区-1号位置"
}
```

响应体：
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "anchorCode": "ANCHOR-001",
        "maxWeight": 1200.00,
        "minWindSpeed": 0.00,
        "maxWindSpeed": 10.00,
        "locationDesc": "基地A区-1号位置",
        "status": 1,
        "createTime": "2024-01-01 10:00:00"
    }
}
```

**GET /api/anchor/filter?minWind=3&maxWind=10**

响应体：
```json
{
    "code": 200,
    "message": "success",
    "data": [
        {
            "id": 1,
            "anchorCode": "ANCHOR-001",
            "maxWeight": 1200.00,
            "minWindSpeed": 0.00,
            "maxWindSpeed": 10.00,
            "locationDesc": "基地A区-1号位置",
            "status": 1
        }
    ]
}
```

### 5.2 航线管理接口

| API路径 | HTTP方法 | Controller | 功能描述 |
|---------|----------|------------|----------|
| /api/route | POST | RouteController | 创建航线 |
| /api/route | GET | RouteController | 查询航线列表 |
| /api/route/{id} | GET | RouteController | 查询航线详情 |
| /api/route/{id} | PUT | RouteController | 更新航线（含气流参数） |
| /api/route/{id} | DELETE | RouteController | 删除航线 |
| /api/route/groups | GET | RouteController | 获取航线分组列表 |

#### 请求/响应示例

**POST /api/route**

请求体：
```json
{
    "routeCode": "ROUTE-001",
    "routeName": "训练航线A",
    "routeGroup": "训练组",
    "windSpeed": 3.50,
    "description": "新手训练专用航线"
}
```

响应体：
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "routeCode": "ROUTE-001",
        "routeName": "训练航线A",
        "routeGroup": "训练组",
        "windSpeed": 3.50,
        "windLevel": "轻风",
        "description": "新手训练专用航线",
        "status": 1,
        "createTime": "2024-01-01 10:00:00"
    }
}
```

**PUT /api/route/{id}**

请求体：
```json
{
    "windSpeed": 8.00
}
```

响应体：
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "windSpeed": 8.00,
        "windLevel": "和风",
        "adaptResult": {
            "rebindCount": 1,
            "unbindCount": 2,
            "logIds": [1, 2, 3]
        }
    }
}
```

### 5.3 适配校验接口

| API路径 | HTTP方法 | Controller | 功能描述 |
|---------|----------|------------|----------|
| /api/adapt/bind | POST | AdaptController | 绑定锚点到航线（含校验） |
| /api/adapt/unbind | POST | AdaptController | 解绑锚点 |
| /api/adapt/check | GET | AdaptController | 校验单个锚点是否适配航线 |
| /api/adapt/recheck/{routeId} | POST | AdaptController | 重新校验航线所有锚点 |
| /api/adapt/logs | GET | AdaptController | 查询适配调整流水 |

#### 请求/响应示例

**POST /api/adapt/bind**

请求体：
```json
{
    "routeId": 1,
    "anchorId": 1
}
```

响应体：
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "valid": true,
        "routeId": 1,
        "anchorId": 1,
        "bindId": 1,
        "reason": "锚点ANCHOR-001适配气流区间[0.00-10.00]覆盖航线气流强度3.50m/s"
    }
}
```

校验失败响应：
```json
{
    "code": 400,
    "message": "适配校验失败",
    "data": {
        "valid": false,
        "routeId": 1,
        "anchorId": 2,
        "reason": "锚点ANCHOR-002适配气流上限5.00m/s小于航线气流强度8.00m/s，禁止绑定"
    }
}
```

**GET /api/adapt/logs?routeId=1**

响应体：
```json
{
    "code": 200,
    "message": "success",
    "data": [
        {
            "id": 1,
            "routeCode": "ROUTE-001",
            "anchorCode": "ANCHOR-001",
            "operationType": "BIND",
            "beforeWindSpeed": null,
            "afterWindSpeed": 3.50,
            "reason": "手动绑定",
            "createTime": "2024-01-01 10:00:00"
        }
    ]
}
```

---

## 六、Redis缓存设计

### 6.1 缓存策略

使用 Redis SortedSet 缓存支架承重参数，按承重值排序，支持快速范围查询。

### 6.2 缓存结构

| Key | 类型 | Score | Value | 说明 |
|-----|------|-------|-------|------|
| anchor:weight | ZSET | max_weight | anchor_code | 锚点承重排序缓存 |
| anchor:wind:min | ZSET | min_wind_speed | anchor_code | 锚点最小风速排序 |
| anchor:wind:max | ZSET | max_wind_speed | anchor_code | 锚点最大风速排序 |

### 6.3 缓存操作

```
写入缓存（锚点创建/更新时）：
ZADD anchor:weight {max_weight} {anchor_code}
ZADD anchor:wind:min {min_wind_speed} {anchor_code}
ZADD anchor:wind:max {max_wind_speed} {anchor_code}

查询适配锚点（按气流区间）：
ZRANGEBYSCORE anchor:wind:max {wind_speed} +INF
→ 获取所有适配风速上限 >= 当前气流强度的锚点

查询适配锚点（按承重）：
ZRANGEBYSCORE anchor:weight {min_weight} +INF
→ 获取所有承重 >= 最低要求的锚点

删除缓存（锚点删除时）：
ZREM anchor:weight {anchor_code}
ZREM anchor:wind:min {anchor_code}
ZREM anchor:wind:max {anchor_code}
```

---

## 七、前端页面设计

### 7.1 页面结构

```
├── / (首页仪表盘)
├── /anchor (锚点管理)
│   ├── 列表页
│   └── 编辑页
├── /route (航线管理)
│   ├── 列表页
│   └── 编辑页
└── /adapt (适配校验)
    ├── 绑定管理页
    └── 流水记录页
```

### 7.2 页面功能说明

#### 7.2.1 锚点管理页面

| 功能区域 | 说明 |
|----------|------|
| 搜索栏 | 锚点编号、位置描述搜索 |
| 筛选控件 | 气流区间联动筛选（滑块选择） |
| 锚点列表 | 展示编号、最大承重、气流区间、位置、状态 |
| 操作按钮 | 新增、编辑、删除、详情 |

#### 7.2.2 航线管理页面

| 功能区域 | 说明 |
|----------|------|
| 搜索栏 | 航线编号、名称搜索 |
| 分组筛选 | 下拉选择航线分组 |
| 航线列表 | 展示编号、名称、分组、气流强度、状态 |
| 操作按钮 | 新增、编辑、删除、绑定锚点、重新校验 |

#### 7.2.3 适配校验页面

| 功能区域 | 说明 |
|----------|------|
| 航线选择 | 选择目标航线 |
| 可用锚点列表 | 展示适配的锚点（自动过滤） |
| 已绑定锚点列表 | 展示已绑定的锚点 |
| 绑定操作 | 绑定/解绑锚点，实时校验 |

#### 7.2.4 流水记录页面

| 功能区域 | 说明 |
|----------|------|
| 筛选条件 | 航线、锚点、操作类型、时间范围 |
| 流水列表 | 展示操作类型、航线、锚点、原因、时间 |
| 导出按钮 | 导出流水记录 |

---

## 八、Docker配置

### 8.1 开发环境 docker-compose.dev.yml

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    container_name: px-base-mysql
    ports:
      - "127.0.0.1:3373:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: px_base
    volumes:
      - mysql-data:/var/lib/mysql
    networks:
      - px-network

  redis:
    image: redis:7-alpine
    container_name: px-base-redis
    ports:
      - "127.0.0.1:6446:6379"
    volumes:
      - redis-data:/data
    networks:
      - px-network

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile.dev
    container_name: px-base-backend
    ports:
      - "127.0.0.1:8157:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DB_HOST: mysql
      DB_PORT: 3306
      REDIS_HOST: redis
      REDIS_PORT: 6379
    depends_on:
      - mysql
      - redis
    volumes:
      - ./backend:/app
      - adapt-logs:/app/logs
    networks:
      - px-network

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile.dev
    container_name: px-base-frontend
    ports:
      - "127.0.0.1:8147:5173"
    volumes:
      - ./frontend:/app
      - /app/node_modules
    networks:
      - px-network

volumes:
  mysql-data:
  redis-data:
  adapt-logs:

networks:
  px-network:
    driver: bridge
```

### 8.2 生产环境 docker-compose.prod.yml

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    container_name: px-base-mysql
    ports:
      - "127.0.0.1:3373:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: px_base
    volumes:
      - mysql-data:/var/lib/mysql
    networks:
      - px-network

  redis:
    image: redis:7-alpine
    container_name: px-base-redis
    ports:
      - "127.0.0.1:6446:6379"
    volumes:
      - redis-data:/data
    networks:
      - px-network

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile.prod
    container_name: px-base-backend
    ports:
      - "127.0.0.1:8157:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: mysql
      DB_PORT: 3306
      REDIS_HOST: redis
      REDIS_PORT: 6379
    depends_on:
      - mysql
      - redis
    volumes:
      - adapt-logs:/app/logs
    networks:
      - px-network

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile.prod
    container_name: px-base-frontend
    ports:
      - "127.0.0.1:8147:80"
    networks:
      - px-network

volumes:
  mysql-data:
  redis-data:
  adapt-logs:

networks:
  px-network:
    driver: bridge
```

### 8.3 镜像源配置

| 组件 | 镜像源 |
|------|--------|
| npm | 中科大镜像 https://mirrors.ustc.edu.cn/npm/ |
| Maven | 网易镜像 http://mirrors.163.com/maven/ |

---

## 九、端口配置

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 | 8177 | Vue开发服务器/生产Nginx |
| 后端 | 8178 | Spring Boot应用 |
| MySQL | 3384 | 数据库服务 |
| Redis | 6457 | 缓存服务 |

---

## 十、部署流程

1. 启动开发环境：`docker-compose -f docker-compose.dev.yml up -d`
2. 启动生产环境：`docker-compose -f docker-compose.prod.yml up -d`
3. 前端访问：http://localhost:8177
4. 后端API：http://localhost:8178/api

---

## 十一、项目目录结构

```
px-base-system/
├── backend/                    # 后端代码
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/px/base/
│   │       │       ├── controller/    # REST控制器
│   │       │       ├── service/       # 业务逻辑
│   │       │       ├── repository/    # 数据访问
│   │       │       ├── entity/        # 实体类
│   │       │       ├── dto/           # 数据传输对象
│   │       │       ├── config/        # 配置类
│   │       │       ├── util/          # 工具类
│   │       │       └── PxBaseApplication.java
│   │       └── resources/
│   │           ├── application.yml
│   │           ├── application-dev.yml
│   │           ├── application-prod.yml
│   │           └── schema.sql
│   ├── Dockerfile.dev
│   ├── Dockerfile.prod
│   └── pom.xml
├── frontend/                   # 前端代码
│   ├── src/
│   │   ├── components/        # 通用组件
│   │   ├── views/             # 页面组件
│   │   ├── api/               # API接口定义
│   │   ├── stores/            # 状态管理
│   │   ├── utils/             # 工具函数
│   │   ├── App.vue
│   │   └── main.ts
│   ├── public/
│   ├── Dockerfile.dev
│   ├── Dockerfile.prod
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── tailwind.config.js
├── docker-compose.dev.yml
├── docker-compose.prod.yml
├── .env
└── tech_doc.md
```
