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

## 十二、航线成组配桩「预演 + 提交」能力

运营先选一条航线，一次性勾选多个地面锚点组成一套配桩方案；系统先做一轮预演体检，确认无误后才允许提交落库。预演与提交调用**同一套判定引擎**（`rule/GroupBindingEvaluator#evaluate`），并采用同一种回退策略，从代码层杜绝"预演说能配、提交又放行别的"。

### 12.1 多约束判定（缺一不可）

对每个锚点独立判定，逐条给出判定码与中文原因：

1. **气流区间真正包住**：`anchor.min_wind_speed <= route.wind_speed`（`WIND_MIN`，下限也查）且 `anchor.max_wind_speed >= route.wind_speed`（`WIND_MAX`）。旧逻辑只比上限，已堵上"只适配大风的锚点配微风航线"被误放行的口子。
2. **承重达标（等级表单一事实来源 `rule/WindWeightRule`）**：
   - `WEIGHT`：锚点承重 ≥ 航线当前风级最低承重——微风 500、轻风 800、和风 1200、强风 1800、疾风 2500 kg；
   - `WEIGHT_LEVEL`：当锚点适配下限高于航线气流（区间向下都够不着）时，承重还须达到其**起始适配风级**的等级门槛。典型样本"只适配强风(下限10)、承重 600kg"配微风：同时命中 `WIND_MIN` 与 `WEIGHT_LEVEL`（强风需 1800kg，差在等级要求上），不会被微风 500 的低门槛放行；而宽区间全能锚不会被误拒。
3. **单锚点唯一占用**（`OCCUPIED`）：一个锚点同一时间只能真正服役于一条启用航线，由新表 `anchor_occupancy`（`anchor_id` 作主键）在数据库层强制；同航线重复配桩记 `ALREADY_BOUND`。
4. 整套层面另给"总承重预算"：合格锚点承重合计 ≥ 当前风级单锚点门槛 × 锚点数（页面与接口均展示）。

### 12.2 一致策略：整套全成或全回退（ALL_OR_NOTHING）

勾选中只要有一个锚点不合格，提交时**整套回退、一条绑定都不落库**，并在页面/接口逐条列出不合格原因；只有全部合格且整套承重预算达标才一次性整体落库。该策略在预演与提交两处完全一致，页面用醒目提示讲清后果（不采用"合格先落、不合格二次确认"的混合口径）。

### 12.3 并发唯一占用

提交在单一事务内：按 `anchorId` 升序对锚点行加悲观写锁（`findByIdForUpdate`，统一加锁顺序防死锁）→ 锁内对主占行 `FOR UPDATE` 复判 → 插入 `anchor_occupancy`。两个运营并发抢同一稀缺锚点时，先锁到者成功；后到者读到主占被判 `OCCUPIED`，或在更弱隔离的库上由**主键/唯一约束兜底**抛 `DataIntegrityViolationException`，服务层补偿缓存并用同一引擎按最新占用复判，返回明确"占用冲突 + 被哪条航线占用"，**最终库里只留一条绑定**。

### 12.4 缓存与数据库一致回滚

- 提交成功：绑定关系、唯一主占、BIND 流水落库后，把锚点承重/气流写入既有三个 SortedSet（`anchor:weight`、`anchor:wind:min`、`anchor:wind:max`，member 为锚点编号）。
- 任一步落库失败：数据库事务整体回滚；服务层用**写前快照**（`AnchorRankCacheService.snapshot/compensate`）把本次已写入缓存的 member 逐条恢复——写前不存在则移除、写前已存在则恢复旧分值，杜绝"缓存有、库里没有"的脏数据；无关锚点缓存不受影响。
- 留痕：配上写 `BIND`；被拒写 `REJECT`；并发冲突写 `OCCUPY_CONFLICT`；落库失败写带补偿说明的 `REJECT`。拒绝/失败流水用 `REQUIRES_NEW` 独立事务，业务回滚也不丢痕，reason 中写明命中的判定码。

### 12.5 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/group-binding/rules` | 风级→最低承重对照表 |
| POST | `/api/group-binding/rehearse` | 预演体检（不落库），返回整套+逐条结论 |
| POST | `/api/group-binding/submit` | 提交；合格整套落库，不合格/冲突/失败均 `committed=false` 且一条不落 |

前端新增页面「成组配桩预演」（`frontend/src/views/GroupBind.vue`，路由 `/group-binding`）。

### 12.6 本地免容器验收（local profile）

生产仍走 docker compose 的 MySQL；本地可免安装数据库，用 H2(MySQL 兼容模式) + 真实 Redis 启动：

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
# 制造"提交中途落库失败"，验证 DB 回滚 + 缓存补偿：
PX_FAULT_ANCHOR_CODE=<锚点编号> SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

local profile 启动时幂等灌入验收样本（含 A-SF600「只适配强风、600kg」、稀缺锚 A-SCARCE-1800），并把启用锚点全量初始化进 Redis 排序缓存。

