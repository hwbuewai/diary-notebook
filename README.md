# 📔 个人日记本 - 支持版本回溯的个人笔记应用

基于 Spring Boot + JGit 的个人日记项目，实现笔记的版本管理和分支切换（灵感来源于 Git）。

## ✅ 已完成功能
- 用户认证（JWT）
- 笔记的增删改查
- 笔记版本回溯（查看历史修改记录）
- 集成 JGit 操作 Git 仓库

## 🛠️ 技术栈
- Java 17
- Spring Boot 2.7
- Spring Security + JWT
- JGit（Git 操作库）
- MySQL + Redis
- Maven

## 📁 项目结构简要
src/main/java/com/example/diary/
├── controller/ # API 接口
├── service/ # 业务逻辑（含 GitService）
├── repository/ # 数据访问
├── model/ # 实体类
└── config/ # 配置类

## 🚀 如何运行
1. 安装 JDK 17 和 MySQL
2. 复制 `application.yml` 中的配置，填入真实数据库和 Redis 信息
3. 运行 `DiaryApplication.java`

## 📌 开发计划
- [ ] 笔记分支管理
- [ ] 分支合并可视化
- [ ] 前端页面

## 📄 项目背景
个人学习项目，用于实践 Spring Boot、JGit、分布式锁、缓存等后端技术。
