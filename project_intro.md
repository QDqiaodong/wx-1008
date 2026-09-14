# 动力伞飞行基地地面固定锚点航线气流区间承重适配系统

## 项目简介

本系统是针对动力伞飞行基地的地面固定锚点管理系统，核心功能是实现航线气流区间与地面锚点承重的智能适配校验，确保飞行安全。

## 核心功能

1. **地面固定锚点基础建档**：编号、最大承重、适配气流区间
2. **动力伞航线绑定锚点**：自动校验气流承重适配范围
3. **航线气流参数更新**：重新匹配适配锚点并留存记录
4. **按气流区间筛选锚点**：联动筛选控件，快速查找适配锚点

## 访问地址

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:8177 |
| 后端API | http://localhost:8178/api |
| MySQL | localhost:3384 |
| Redis | localhost:6457 |

## 技术栈

- **前端**：Vue3 + Vite + Element Plus + TypeScript + Tailwind CSS
- **后端**：Spring Boot 3.3 + JDK 17 + Spring Data JPA
- **数据库**：MySQL 8.0
- **缓存**：Redis 7.x（SortedSet缓存支架承重参数）
- **容器化**：Docker + Docker Compose

## 启动方式

### 开发环境
```bash
docker-compose -f docker-compose.dev.yml up -d
```

### 生产环境
```bash
docker-compose -f docker-compose.prod.yml up -d
```

## 项目文档

详细技术方案请查看：[tech_doc.md](tech_doc.md)
