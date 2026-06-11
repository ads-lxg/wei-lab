# Lab OA 测试计划与测试工程师方法论

## 一、测试工程师视角的完整测试策略

### 1.1 测试分层模型

```
┌─────────────────────────────────────────────────────────────┐
│  第一层：单元测试 (Unit Test)                                  │
│  - 每个 Service 方法的逻辑正确性                                │
│  - 每个工具类的方法边界条件                                     │
│  - 使用 JUnit 5 + Mockito                                     │
├─────────────────────────────────────────────────────────────┤
│  第二层：集成测试 (Integration Test)                           │
│  - Controller → Service → Mapper → DB 的完整链路              │
│  - 使用 @SpringBootTest + Testcontainers                      │
├─────────────────────────────────────────────────────────────┤
│  第三层：接口测试 (API Test) - 当前重点                        │
│  - 使用 Apifox / Postman / JMeter                             │
│  - 验证请求响应、状态码、数据格式、业务规则                      │
├─────────────────────────────────────────────────────────────┤
│  第四层：端到端测试 (E2E Test)                                 │
│  - 前端 + 后端的完整业务流程                                    │
│  - 使用 Selenium / Playwright                                 │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 测试工程师的核心工作流程

```
Day 1: 需求分析 → 编写测试计划 → 设计测试用例
Day 2-3: 搭建测试环境 → 准备测试数据
Day 4-7: 执行测试 → 记录缺陷 → 回归验证
Day 8: 输出测试报告 → 风险评估
```

---

## 二、Lab OA 接口测试用例全集

### 2.1 测试用例设计方法论

每个接口至少覆盖以下维度：

| 维度 | 说明 | 示例 |
|------|------|------|
| **正向场景** | 正常参数，预期成功 | 正确用户名密码登录 |
| **边界值** | 参数取边界值 | 密码恰好6位、恰好200位 |
| **异常参数** | 非法参数，预期失败 | 空用户名、超长字符串 |
| **权限验证** | 不同角色访问控制 | guest 尝试下载文献 |
| **并发测试** | 多用户同时操作 | 同时上传文件 |
| **数据一致性** | 操作后数据状态正确 | 删除后数据库是否软删除 |

---

### 2.2 用户认证模块

#### TC-AUTH-001: 正常登录
```
接口: POST /api/user/login
输入: {"username":"admin","password":"123456"}
预期: code=200, data.token 不为空, data.user.username="admin"
```

#### TC-AUTH-002: 错误密码
```
接口: POST /api/user/login
输入: {"username":"admin","password":"wrong"}
预期: code=500, message 包含密码错误
```

#### TC-AUTH-003: 空用户名
```
接口: POST /api/user/login
输入: {"username":"","password":"123456"}
预期: code=400, 参数校验失败
```

#### TC-AUTH-004: 用户名不存在
```
接口: POST /api/user/login
输入: {"username":"notexist","password":"123456"}
预期: code=500, 用户不存在
```

#### TC-AUTH-005: 密码过短
```
接口: POST /api/user/register
输入: {"username":"test","password":"123","email":"test@test.com"}
预期: code=400, 密码长度不足
```

#### TC-AUTH-006: 邮箱格式错误
```
接口: POST /api/user/register
输入: {"username":"test","password":"123456","email":"invalid"}
预期: code=400, 邮箱格式错误
```

#### TC-AUTH-007: 重复注册
```
接口: POST /api/user/register
输入: {"username":"admin","password":"123456","email":"admin@laboa.com"}
预期: code=500, 用户名或邮箱已存在
```

#### TC-AUTH-008: Token 过期后访问
```
接口: GET /api/user/info
输入: Authorization: 过期token
预期: code=401, 未登录或token已过期
```

---

### 2.3 用户管理模块（管理员权限）

#### TC-USER-001: 正常分页查询
```
接口: GET /api/admin/user/page?page=1&size=10
角色: admin
预期: code=200, data.total >= 4, data.records[0].username 不为空
```

#### TC-USER-002: 带关键词搜索
```
接口: GET /api/admin/user/page?page=1&size=10&keyword=admin
角色: admin
预期: code=200, data.records 中至少有一条 username 包含 admin
```

#### TC-USER-003: 非管理员访问用户管理
```
接口: GET /api/admin/user/page?page=1&size=10
角色: student
预期: code=403, 无权限
```

#### TC-USER-004: 禁用用户后登录
```
前置: PUT /api/admin/user/3/status?status=0 (禁用 student)
接口: POST /api/user/login
输入: {"username":"student","password":"123456"}
预期: code=500, 账号已被禁用
后置: PUT /api/admin/user/3/status?status=1 (恢复)
```

#### TC-USER-005: 分配角色
```
接口: PUT /api/admin/user/3/roles
输入: [2, 3]
角色: admin
预期: code=200, 查询该用户角色已更新
```

---

### 2.4 文件上传模块

#### TC-FILE-001: 正常上传 PDF
```
接口: POST /api/file/upload
输入: file=test.pdf (大小 < 100MB)
预期: code=200, data.mimeType="application/pdf", data.md5 不为空
```

#### TC-FILE-002: 上传超大文件
```
接口: POST /api/file/upload
输入: file=200MB.zip
预期: code=500, 文件大小超限
```

#### TC-FILE-003: 上传空文件
```
接口: POST /api/file/upload
输入: file=empty.txt (0字节)
预期: code=200 或 code=500 (根据业务定义)
```

#### TC-FILE-004: 未登录上传
```
接口: POST /api/file/upload
输入: 无 Authorization
预期: code=401, 未登录
```

#### TC-FILE-005: 获取预签名URL
```
前置: 上传文件获取 fileId
接口: GET /api/file/{fileId}/url
预期: code=200, 返回的URL可直接下载文件
```

#### TC-FILE-006: 获取不存在的文件URL
```
接口: GET /api/file/999999/url
预期: code=500, 文件不存在
```

---

### 2.5 内部文档模块

#### TC-DOC-001: 创建文档
```
前置: 上传文件获取 fileId
接口: POST /api/doc
输入: title="测试文档", file=test.md
预期: code=200, 数据库 md_document 表新增记录, status=1(草稿)
```

#### TC-DOC-002: 空标题创建
```
接口: POST /api/doc
输入: title="", file=test.md
预期: code=400, 标题不能为空
```

#### TC-DOC-003: 分页查询
```
接口: GET /api/doc/page?page=1&size=10&keyword=Spring
预期: code=200, 返回的 records 中 title 包含 Spring
```

#### TC-DOC-004: 更新文档状态
```
前置: 创建文档获取 docId
接口: PUT /api/doc/{docId}/status?status=2
预期: code=200, 数据库 status=2(发布)
```

#### TC-DOC-005: 删除文档
```
接口: DELETE /api/doc/{docId}
预期: code=200, 数据库 deleted=1(逻辑删除)
```

#### TC-DOC-006: 获取已删除文档
```
前置: 删除文档
接口: GET /api/doc/{docId}
预期: code=500, 文档不存在
```

---

### 2.6 文献管理模块

#### TC-LIT-001: 创建文献
```
接口: POST /api/literature
输入: literature={"title":"测试论文","authors":"张三","abstract":"摘要","keywords":"AI","publishYear":2024,"permissionLevel":2}, file=paper.pdf
预期: code=200, viewCount=0, downloadCount=0
```

#### TC-LIT-002: 游客查看文献列表
```
接口: GET /api/literature?page=1&size=10
角色: guest
预期: code=200, 可以正常查看
```

#### TC-LIT-003: 游客下载文献（权限控制）
```
前置: 创建 permissionLevel=2 的文献
接口: GET /api/literature/{id}/download
角色: guest
预期: code=403, 无下载权限
```

#### TC-LIT-004: 学生下载文献
```
接口: GET /api/literature/{id}/download
角色: student
预期: code=200, 返回下载链接, downloadCount+1
```

#### TC-LIT-005: 浏览次数递增
```
前置: 记录当前 viewCount
接口: GET /api/literature/{id}
预期: code=200, viewCount = 原值 + 1
```

#### TC-LIT-006: 按年份筛选
```
接口: GET /api/literature?page=1&size=10&publishYear=2024
预期: code=200, 所有记录的 publishYear=2024
```

#### TC-LIT-007: 按作者搜索
```
接口: GET /api/literature?page=1&size=10&author=张三
预期: code=200, 返回的 records 中 authors 包含 张三
```

---

### 2.7 全局搜索模块

#### TC-SEARCH-001: 全文搜索
```
接口: GET /api/search?keyword=Spring&type=all&page=1&size=10
预期: code=200, hits 中至少有一条 title 或 abstract 包含 Spring
```

#### TC-SEARCH-002: 仅搜索文档
```
接口: GET /api/search?keyword=Spring&type=doc
预期: code=200, 返回的 hits 中 type 全部为 doc
```

#### TC-SEARCH-003: 空关键词
```
接口: GET /api/search?keyword=&type=all
预期: code=200, hits 为空或返回全部
```

#### TC-SEARCH-004: 搜索无结果
```
接口: GET /api/search?keyword=不存在的词&type=all
预期: code=200, hits 为空数组
```

---

### 2.8 通知模块

#### TC-NOTIF-001: 发送通知
```
前置: WebSocket 连接 /ws
接口: 通过业务操作触发通知（如上传文献）
预期: 收到 WebSocket 消息, notification 表新增记录
```

#### TC-NOTIF-002: 分页查询未读通知
```
接口: GET /api/notification?isRead=0&page=1&size=10
预期: code=200, 返回的 records 中 isRead 全部为 0
```

#### TC-NOTIF-003: 标记已读
```
前置: 获取未读通知 id
接口: PUT /api/notification/{id}/read
预期: code=200, 该通知 isRead=1
```

#### TC-NOTIF-004: 未读数量
```
接口: GET /api/notification/unread-count
预期: code=200, 返回未读通知总数
```

---

### 2.9 RAG 问答模块

#### TC-RAG-001: 正常提问
```
接口: POST /api/rag/chat
输入: {"question":"实验室有哪些文献？"}
预期: SSE 流式返回, 内容不为空
```

#### TC-RAG-002: 空问题
```
接口: POST /api/rag/chat
输入: {"question":""}
预期: code=400 或返回提示信息
```

---

## 三、Apifox 自动化测试配置

### 3.1 环境配置

创建环境 `Lab OA Dev`：

| 变量名 | 类型 | 初始值 | 说明 |
|--------|------|--------|------|
| `baseUrl` | 常量 | `http://localhost:8080` | 基础URL |
| `token` | 动态 | (空) | 登录后自动填充 |
| `adminToken` | 动态 | (空) | admin 账号token |
| `studentToken` | 动态 | (空) | student 账号token |
| `guestToken` | 动态 | (空) | guest 账号token |
| `fileId` | 动态 | (空) | 上传文件后保存 |
| `docId` | 动态 | (空) | 创建文档后保存 |
| `literatureId` | 动态 | (空) | 创建文献后保存 |

### 3.2 测试步骤（Test Step）配置

在 Apifox 中创建「测试步骤」按顺序执行：

```
Step 1:  admin 登录 → 保存 token 到 adminToken
Step 2:  student 登录 → 保存 token 到 studentToken
Step 3:  guest 登录 → 保存 token 到 guestToken
Step 4:  admin 上传文件 → 保存 fileId
Step 5:  admin 创建文档 → 保存 docId
Step 6:  admin 创建文献 → 保存 literatureId
Step 7:  搜索测试
Step 8:  权限测试（guest 下载文献 → 预期 403）
Step 9:  student 下载文献 → 预期 200
Step 10: 通知测试
Step 11: RAG 测试
Step 12: 清理数据（删除文档、文献）
```

### 3.3 后置操作脚本示例

**登录接口后置脚本**：
```javascript
const response = pm.response.json();
if (response.code === 200 && response.data && response.data.token) {
    pm.environment.set("token", response.data.token);
    // 根据用户名保存到不同变量
    const username = JSON.parse(pm.request.body.raw).username;
    if (username === 'admin') {
        pm.environment.set("adminToken", response.data.token);
    } else if (username === 'student') {
        pm.environment.set("studentToken", response.data.token);
    } else if (username === 'guest') {
        pm.environment.set("guestToken", response.data.token);
    }
}
```

**上传文件后置脚本**：
```javascript
const response = pm.response.json();
if (response.code === 200 && response.data) {
    pm.environment.set("fileId", response.data.id);
}
```

---

## 四、性能测试建议

### 4.1 使用 JMeter 进行压力测试

| 场景 | 并发数 | 持续时间 | 预期指标 |
|------|--------|----------|----------|
| 登录接口 | 100 | 60s | 平均响应 < 200ms, 错误率 < 1% |
| 文件上传 | 20 | 60s | 平均响应 < 3s, 无内存溢出 |
| 搜索接口 | 50 | 60s | 平均响应 < 500ms |
| 文献下载 | 30 | 60s | 平均响应 < 1s |

### 4.2 使用 Apifox 自动化测试

设置「自动化测试」定时执行：
- 每天早上 9 点执行全量回归
- 每次代码提交后触发冒烟测试

---

## 五、缺陷报告模板

```
【缺陷编号】BUG-001
【模块】用户认证
【严重程度】严重
【优先级】高
【标题】空密码可以成功登录
【复现步骤】
1. 打开登录页面
2. 输入用户名 admin
3. 密码留空
4. 点击登录
【预期结果】提示密码不能为空
【实际结果】成功登录，返回 token
【环境】Windows 11, Chrome 120, 后端 v1.0.0
【附件】截图/日志
```

---

## 六、测试完成标准

| 检查项 | 标准 |
|--------|------|
| 功能覆盖率 | 100% 接口至少有一条正向用例 |
| 边界值覆盖 | 所有必填参数都有空值/超长/格式错误测试 |
| 权限覆盖 | 每个受保护接口都用不同角色测试 |
| 缺陷关闭率 | 严重/高优先级缺陷 100% 关闭 |
| 自动化率 | 核心业务流程 80% 以上自动化 |
| 性能达标 | 所有接口响应时间 < 3s |
