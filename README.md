# AIMUNC 2026 会议报名与信息管理平台

> 公开、脱敏的作品集源码快照；用于学术与职业评审，不是会议信息系统的现行运营仓库。

面向约 250–300 人规模模拟联合国会议设计的多角色报名与信息管理平台。项目于 2026 年 1–3 月从 v0.1 迭代至 v3.5，完成可运行系统与初始服务器部署，承载从信息提交、领队初审到管理员终审、会场分配和付款审核的业务流程。

这是我的第一个独立全栈项目。除业务需求、运营规则和验收反馈与社团协作外，我是唯一的人类开发者，独立承担产品梳理、系统架构、前后端开发、管理端设计、测试、初始部署和技术交接。项目最终以人民币 1,500 元向社团完成一次性有偿交付；交付后的正式上线决策、真实数据运营和持续运维由社团负责。开发过程中使用 Claude 辅助架构讨论、实现、排错和审计；平台本身没有内置 Agent 功能。

> 事实边界：250–300 人是目标会议规模；早期版本通过小范围测试收集反馈。据项目负责人确认，社团接管后的平台在正式使用阶段实际服务 237 名代表。237 人与人民币 1,500 元交付金额目前均为负责人确认、待补脱敏佐证；前者不代表账号数、并发量或性能结果，后者不代表持续营收或知识产权转让。仓库不包含任何真实报名数据。
## 给评审老师的阅读入口

- [项目概览](docs/teacher-brief.md)：问题、个人贡献、成果与研究方向关联。
- [系统架构](docs/architecture.md)：三角色业务闭环、数据模型和部署结构。
- [开发时间线](docs/development-timeline.md)：v0.1 至 v3.5 的迭代过程。
- [AI 辅助开发](docs/ai-assisted-development.md)：AI 的真实作用及本人责任边界。
- [已知不足与 V2 路线](docs/known-limitations-and-v2.md)：首版技术债与 Python/Agent 演进设想。
- [证据与主张边界](docs/evidence-and-claims.md)：哪些结论已核验、哪些仍待确认。

## 解决的问题

大型学生会议依赖表格和聊天工具收集信息时，容易出现数据分散、状态不统一、重复沟通和管理困难。本项目把不同角色的工作流统一到同一套数据模型和 REST API 上：

1. 代表注册并提交个人信息、志愿和付款凭证。
2. 领队创建代表团、邀请成员并进行初审。
3. 管理员查看全局统计，完成终审、会场分配、付款审核和数据导出。
4. 各角色重新请求数据后看到同一后端状态的最新结果。

这里的“多端联动”是基于共享数据库与 API 的状态一致性，不是 WebSocket 实时推送。

## 核心能力

- 三角色体系：`DELEGATE`、`LEADER`、`ADMIN`。
- 建团与邀请码、代表报名、三级志愿、两级审核状态流。
- 管理端代表/代表团双视图、统计看板、多维筛选、排序和分页。
- 会场分配、付款凭证上传与审核、筛选结果 Excel 导出。
- BCrypt 密码哈希、JWT 身份传递、角色及资源归属校验。
- 深浅主题、响应式页面和 Three.js 数据可视化背景。
- VPS + Cloudflare + Nginx + systemd 部署记录。

## 技术栈与规模

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5.9、Spring Web、Spring Data JPA、Spring Security、JWT |
| 数据 | MySQL；测试使用 H2 内存数据库 |
| 前端 | HTML、CSS、原生 JavaScript、Bootstrap 5、Three.js、SheetJS |
| 部署 | VPS、Cloudflare、Nginx、systemd |

当前快照约含 15 个主要 Java 文件、9 个业务页面、20 个 REST 接口和约 1.16 万行文本。行数包含 Java、HTML、CSS、JavaScript、注释与页面样式，不等同于纯业务代码行。

## 目录

```text
src/main/java/                  Spring Boot 后端
src/main/resources/static/     多角色静态前端
src/main/resources/            环境变量驱动的安全配置
src/test/                      H2 测试配置与上下文测试
docs/                          教师评审与作品集材料
scripts/repository_check.py    隐私和敏感文件防误提交检查
.github/workflows/ci.yml       安全检查与 Maven 测试
```

## 安全说明

本仓库是从历史工程包按白名单重建的作品展示快照。以下内容已排除：

- 真实数据库配置、JWT 密钥和部署凭据；
- IDE HTTP 请求历史、测试身份资料和真实联系方式；
- `target/`、JAR、class、原始 ZIP 和本机路径；
- 版权来源未确认的背景音乐；
- 真实报名信息、付款凭证和数据库导出。

同时完成了发布前的最小安全加固：限制管理员创建路径、保护成员列表与账号撤销接口，并统一前端 JWT 会话存储。详情见 [SECURITY.md](SECURITY.md)。历史环境使用过的数据库密码与 JWT 密钥仍应在原服务器上轮换，因为从仓库删除不等于使旧凭据失效。

公开访问不等于授予开源或商业复用许可。会议名称与标识仅用于记录本人参与开发的历史项目，不代表组织方对本作品集的背书；相关标识权利仍归其权利人。详见 [LICENSE.md](LICENSE.md) 与 [隐私、来源说明](docs/privacy-and-provenance.md)。

## 本地验证

无需真实数据库即可运行仓库安全检查与 H2 上下文测试：

```powershell
python scripts/repository_check.py
.\mvnw.cmd --batch-mode test
```

运行应用需要自行准备 MySQL，并通过环境变量提供配置：

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3306/aimunc2026'
$env:SPRING_DATASOURCE_USERNAME = '<local-user>'
$env:SPRING_DATASOURCE_PASSWORD = '<local-password>'
$env:JWT_SECRET = '<至少 32 字节的随机值>'
$env:AIMUNC_UPLOAD_PATH = '.\data\uploads\payments'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'update' # 仅限可丢弃的本地开发库
.\mvnw.cmd spring-boot:run
```

历史包没有提供数据库迁移文件，因此当前快照还不能在生产环境中一键复现。不要把 `ddl-auto=update` 用于真实生产库。

## 项目定位

这个仓库保留第一版项目的真实能力与不足。它证明的是：我能够理解具体业务、设计产品流程，并独立推进开发、测试、初始部署与一次性付费交付。交付后的上线运营和持续运维由社团接管，不归入我的个人职责。未来的 Python/Agent V2 将作为独立演进版本，不会覆盖或伪装这段历史。
