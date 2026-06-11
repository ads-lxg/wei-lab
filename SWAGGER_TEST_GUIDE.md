# Lab OA Swagger UI 完整测试指南

## 前置准备

1. 确保后端运行在 `http://localhost:8080`
2. 浏览器打开 `http://localhost:8080/swagger-ui.html`
3. 你会看到 Knife4j 增强的 API 文档界面

---

## 全局操作：登录并设置 Token

> ⚠️ **必须先完成这一步，否则其他接口全部报 401**

### Step 0：登录获取 Token

1. 在左侧接口列表找到 **auth-controller** → 展开
2. 点击 **POST /api/user/login**
3. 点击 **Try it out**
4. 在 Request body 中输入：

```json
{
  "username": "admin",
  "password": "123456"
}
```

5. 点击 **Execute**
6. 在 Response body 中找到 `"token": "xxxxx"`
7. **复制 token 值**（只复制引号内的字符串，不含引号）
8. 点击页面顶部 **Authorize** 按钮（锁头图标）
9. 在输入框中粘贴 token → 点击 **Authorize** → 点击 **Close**

> ✅ 设置成功后，后续所有请求会自动带上 Authorization 头

---

## 一、用户认证模块（3 个接口）

### 1.1 POST /api/user/login — 用户登录

| 测试场景 | username | password | 预期结果 |
|----------|----------|----------|----------|
| ✅ 正常登录 | `admin` | `123456` | code=200, token 不为空 |
| ❌ 错误密码 | `admin` | `wrong123` | code=500, 密码错误 |
| ❌ 用户不存在 | `nobody` | `123456` | code=500, 用户不存在 |
| ❌ 空用户名 | (空) | `123456` | code=400, 参数校验失败 |
| ❌ 空密码 | `admin` | (空) | code=400, 参数校验失败 |

**操作步骤**：
1. 展开 POST /api/user/login → Try it out
2. 修改 JSON 中的 username 和 password
3. 点击 Execute
4. 查看 Server response 的 code 和 message

---

### 1.2 POST /api/user/register — 用户注册

| 测试场景 | username | password | email | 预期结果 |
|----------|----------|----------|-------|----------|
| ✅ 正常注册 | `newuser1` | `123456` | `new1@test.com` | code=200 |
| ❌ 用户名已存在 | `admin` | `123456` | `x@test.com` | code=500, 用户名已存在 |
| ❌ 邮箱已存在 | `newuser2` | `123456` | `admin@laboa.com` | code=500, 邮箱已存在 |
| ❌ 邮箱格式错误 | `newuser3` | `123456` | `invalid` | code=400, 邮箱格式错误 |
| ❌ 密码过短 | `newuser4` | `123` | `x@test.com` | code=400, 密码至少6位 |
| ❌ 用户名为空 | (空) | `123456` | `x@test.com` | code=400, 参数校验失败 |

---

### 1.3 GET /api/user/info — 获取当前用户信息

| 测试场景 | 预期结果 |
|----------|----------|
| ✅ 已登录状态 | code=200, 返回 username/roles/email 等 |
| ❌ 未登录（先点 Authorize → Logout） | code=401 或 500, 未登录 |

**操作步骤**：
1. 展开 GET /api/user/info → Try it out → Execute
2. 确认返回的 username 是当前登录用户

---

## 二、用户管理模块（3 个接口，需 admin 角色）

> 先用 admin 账号登录获取 token

### 2.1 GET /api/admin/user/page — 用户分页查询

| 测试场景 | page | size | keyword | 预期结果 |
|----------|------|------|---------|----------|
| ✅ 默认查询 | `1` | `10` | (空) | code=200, total>=4 |
| ✅ 按关键词搜索 | `1` | `10` | `admin` | records 中包含 admin |
| ✅ 搜索无结果 | `1` | `10` | `zzzzz` | total=0 |
| ✅ 第二页 | `2` | `2` | (空) | records 最多2条 |

**操作步骤**：
1. 展开 GET /api/admin/user/page → Try it out
2. 填写 page / size / keyword 参数
3. 点击 Execute

---

### 2.2 PUT /api/admin/user/{id}/status — 修改用户状态

| 测试场景 | id | status | 预期结果 |
|----------|-----|--------|----------|
| ✅ 禁用用户 | `3` | `0` | code=200, student 账号被禁用 |
| ✅ 启用用户 | `3` | `1` | code=200, student 账号恢复 |

**验证方法**：禁用后用 student 账号登录，应该登录失败

---

### 2.3 PUT /api/admin/user/{id}/roles — 分配角色

| 测试场景 | id | roles | 预期结果 |
|----------|-----|-------|----------|
| ✅ 分配教师+学生角色 | `3` | `[2, 3]` | code=200 |

**操作步骤**：
1. 展开 PUT /api/admin/user/{id}/roles → Try it out
2. id 填 `3`
3. Request body 填 `[2, 3]`
4. 点击 Execute

---

## 三、角色管理模块（4 个接口，需 admin 角色）

### 3.1 GET /api/admin/role/list — 角色列表

| 测试场景 | 预期结果 |
|----------|----------|
| ✅ 查询所有角色 | code=200, 返回 4 个角色(admin/teacher/student/guest) |

---

### 3.2 POST /api/admin/role — 创建角色

| 测试场景 | roleCode | roleName | description | 预期结果 |
|----------|----------|----------|-------------|----------|
| ✅ 正常创建 | `visitor` | `访客` | `仅可浏览` | code=200 |
| ❌ 角色编码重复 | `admin` | `管理员2` | `xxx` | code=500, 编码已存在 |

Request body：
```json
{
  "roleCode": "visitor",
  "roleName": "访客",
  "description": "仅可浏览公开内容"
}
```

---

### 3.3 PUT /api/admin/role — 更新角色

| 测试场景 | 预期结果 |
|----------|----------|
| ✅ 修改角色描述 | code=200 |

Request body（先从角色列表获取一个 id）：
```json
{
  "id": 5,
  "roleCode": "visitor",
  "roleName": "访客",
  "description": "更新后的描述"
}
```

---

### 3.4 DELETE /api/admin/role/{id} — 删除角色

| 测试场景 | id | 预期结果 |
|----------|-----|----------|
| ✅ 删除刚创建的角色 | (上一步创建的角色id) | code=200 |
| ❌ 删除不存在的角色 | `999999` | code=500 |

---

## 四、文件上传模块（4 个接口）

### 4.1 POST /api/file/upload — 上传文件

| 测试场景 | 文件 | 预期结果 |
|----------|------|----------|
| ✅ 上传 PDF | 选择一个 .pdf 文件 | code=200, mimeType=application/pdf |
| ✅ 上传 TXT | 选择一个 .txt 文件 | code=200, mimeType=text/plain |
| ✅ 上传图片 | 选择一个 .png 文件 | code=200, mimeType=image/png |
| ❌ 未登录上传 | (先 Logout) | code=401 |

**操作步骤**：
1. 展开 POST /api/file/upload → Try it out
2. 点击 **Choose File** 选择本地文件
3. 点击 Execute
4. **记录返回的 id**（后续接口要用）

---

### 4.2 GET /api/file/{id}/url — 获取文件预签名 URL

| 测试场景 | id | 预期结果 |
|----------|-----|----------|
| ✅ 正常获取 | (上传返回的 id) | code=200, 返回 MinIO 下载链接 |
| ❌ 不存在的文件 | `999999` | code=500 |

**验证方法**：复制返回的 URL 到浏览器新标签页打开，应该能下载文件

---

### 4.3 GET /api/file/{id}/info — 获取文件信息

| 测试场景 | id | 预期结果 |
|----------|-----|----------|
| ✅ 正常获取 | (上传返回的 id) | code=200, 返回 originalName/fileSize/md5 等 |

---

### 4.4 DELETE /api/file/{id} — 删除文件

| 测试场景 | id | 预期结果 |
|----------|-----|----------|
| ✅ 正常删除 | (上传返回的 id) | code=200 |
| ✅ 再次获取已删除文件 | (同上 id) | code=500, 文件不存在 |

---

## 五、内部文档模块（6 个接口）

### 5.1 POST /api/doc — 创建文档

| 测试场景 | title | file | 预期结果 |
|----------|-------|------|----------|
| ✅ 正常创建 | `Spring Boot 指南` | 选择 .md 文件 | code=200, status=1(草稿) |
| ✅ 上传 PDF 文档 | `论文模板` | 选择 .pdf 文件 | code=200 |
| ❌ 未登录 | (先 Logout) | — | code=401 |

**操作步骤**：
1. 展开 POST /api/doc → Try it out
2. title 填 `Spring Boot 指南`
3. file 点击 Choose File 选择本地文件
4. 点击 Execute
5. **记录返回的 id**（后续接口要用）

---

### 5.2 GET /api/doc/page — 文档分页查询

| 测试场景 | page | size | keyword | status | 预期结果 |
|----------|------|------|---------|--------|----------|
| ✅ 默认查询 | `1` | `10` | (空) | (空) | code=200, 返回文档列表 |
| ✅ 按标题搜索 | `1` | `10` | `Spring` | (空) | records 中 title 含 Spring |
| ✅ 按状态筛选 | `1` | `10` | (空) | `1` | 只返回草稿 |
| ✅ 搜索无结果 | `1` | `10` | `zzzzz` | (空) | total=0 |

---

### 5.3 GET /api/doc/{id} — 获取文档详情

| 测试场景 | id | 预期结果 |
|----------|-----|----------|
| ✅ 正常获取 | (创建返回的 id) | code=200, 返回 title/fileId/url |
| ❌ 不存在的文档 | `999999` | code=500 |

---

### 5.4 PUT /api/doc/{id} — 更新文档

| 测试场景 | title | file | 预期结果 |
|----------|-------|------|----------|
| ✅ 只改标题 | `更新后的标题` | 不选文件 | code=200, 标题已更新 |
| ✅ 改标题+换文件 | `换文件了` | 选择新文件 | code=200 |

---

### 5.5 PUT /api/doc/{id}/status — 更新文档状态

| 测试场景 | status | 预期结果 |
|----------|--------|----------|
| ✅ 发布 | `2` | code=200, 文档变为发布状态 |
| ✅ 归档 | `3` | code=200, 文档变为归档状态 |
| ✅ 改回草稿 | `1` | code=200 |

**操作步骤**：
1. 展开 PUT /api/doc/{id}/status → Try it out
2. id 填文档 id
3. status 填 `2`
4. 点击 Execute

---

### 5.6 DELETE /api/doc/{id} — 删除文档

| 测试场景 | id | 预期结果 |
|----------|-----|----------|
| ✅ 正常删除 | (文档 id) | code=200 |
| ✅ 再查询已删除的 | (同上 id) | code=500, 文档不存在 |

---

## 六、文献管理模块（6 个接口）

### 6.1 POST /api/literature — 创建文献

| 测试场景 | 预期结果 |
|----------|----------|
| ✅ 正常创建 | code=200, viewCount=0, downloadCount=0 |
| ❌ 未登录 | code=401 |

**操作步骤**：
1. 展开 POST /api/literature → Try it out
2. **literature** 字段（类型 text）填入：

```json
{
  "title": "基于深度学习的图像识别研究",
  "authors": "张三,李四",
  "abstract": "本文提出了一种基于深度学习的图像识别方法",
  "keywords": "深度学习,图像识别,CNN",
  "publishYear": 2024,
  "doi": "10.1234/test.2024.001",
  "permissionLevel": 2
}
```

3. **file** 字段（类型 file）选择一个 PDF 文件
4. 点击 Execute
5. **记录返回的 id**

---

### 6.2 GET /api/literature — 文献分页查询

| 测试场景 | keyword | author | publishYear | 预期结果 |
|----------|---------|--------|-------------|----------|
| ✅ 默认查询 | (空) | (空) | (空) | code=200 |
| ✅ 按标题搜索 | `深度学习` | (空) | (空) | records 中 title 含深度学习 |
| ✅ 按作者搜索 | (空) | `张三` | (空) | records 中 authors 含张三 |
| ✅ 按年份筛选 | (空) | (空) | `2024` | records 中 publishYear=2024 |
| ✅ 组合搜索 | `深度学习` | `张三` | `2024` | 同时满足 |
| ✅ 搜索无结果 | `zzzzz` | (空) | (空) | total=0 |

---

### 6.3 GET /api/literature/{id} — 获取文献详情

| 测试场景 | id | 预期结果 |
|----------|-----|----------|
| ✅ 正常获取 | (创建返回的 id) | code=200, viewCount 比上次+1 |
| ❌ 不存在的文献 | `999999` | code=500 |

> 💡 每次调用这个接口，viewCount 会自动 +1，可以连续调用两次验证

---

### 6.4 PUT /api/literature/{id} — 更新文献

| 测试场景 | 预期结果 |
|----------|----------|
| ✅ 修改标题和摘要 | code=200 |

Request body：
```json
{
  "id": (文献id),
  "title": "更新后的论文标题",
  "abstract": "更新后的摘要内容"
}
```

---

### 6.5 GET /api/literature/{id}/download — 下载文献

| 测试场景 | 登录角色 | 预期结果 |
|----------|----------|----------|
| ✅ admin 下载 | admin | code=200, 返回下载链接 |
| ✅ teacher 下载 | teacher | code=200, 返回下载链接 |
| ✅ student 下载 | student | code=200, 返回下载链接 |
| ❌ guest 下载 | guest | code=403, 无下载权限 |

**测试 guest 权限的步骤**：
1. 先点 Authorize → Logout
2. 用 guest 账号重新登录：`{"username":"guest","password":"123456"}`
3. 重新设置 Authorize 为 guest 的 token
4. 访问下载接口 → 预期 403

---

### 6.6 DELETE /api/literature/{id} — 删除文献

| 测试场景 | id | 预期结果 |
|----------|-----|----------|
| ✅ 正常删除 | (文献 id) | code=200 |
| ✅ 再查询已删除 | (同上 id) | code=500 |

---

## 七、全局搜索模块（1 个接口）

### 7.1 GET /api/search — 全局搜索

| 测试场景 | keyword | type | 预期结果 |
|----------|---------|------|----------|
| ✅ 全文搜索 | `Spring` | `all` | code=200, 返回匹配结果 |
| ✅ 仅搜索文档 | `Spring` | `doc` | hits 中 type 全为 doc |
| ✅ 仅搜索文献 | `深度学习` | `literature` | hits 中 type 全为 literature |
| ✅ 空关键词 | (空) | `all` | code=200, hits 为空 |
| ✅ 搜索无结果 | `zzzzz` | `all` | code=200, hits 为空数组 |

> ⚠️ 搜索功能依赖 ES 索引，需要先创建文档/文献后才能搜到内容

---

## 八、通知模块（3 个接口）

### 8.1 GET /api/notification — 通知列表

| 测试场景 | isRead | 预期结果 |
|----------|--------|----------|
| ✅ 查询全部 | (不填) | code=200 |
| ✅ 只看未读 | `0` | records 中 isRead 全为 0 |
| ✅ 只看已读 | `1` | records 中 isRead 全为 1 |

---

### 8.2 PUT /api/notification/{id}/read — 标记已读

| 测试场景 | 预期结果 |
|----------|----------|
| ✅ 标记已读 | code=200, 再次查询该通知 isRead=1 |

**操作步骤**：
1. 先调用通知列表获取一个未读通知的 id
2. 调用标记已读接口
3. 再次查询通知列表验证 isRead 已变为 1

---

### 8.3 GET /api/notification/unread-count — 未读数量

| 测试场景 | 预期结果 |
|----------|----------|
| ✅ 查询未读数 | code=200, 返回数字 |

---

## 九、RAG 问答模块（1 个接口）

### 9.1 POST /api/rag/chat — AI 问答

| 测试场景 | question | 预期结果 |
|----------|----------|----------|
| ✅ 正常提问 | `实验室有哪些文献？` | SSE 流式返回 |
| ✅ 提问文档相关 | `Spring Boot 怎么用？` | 返回相关文档内容 |
| ❌ 空问题 | (空) | 返回错误提示 |

**操作步骤**：
1. 展开 POST /api/rag/chat → Try it out
2. Request body：

```json
{
  "question": "实验室有哪些关于深度学习的文献？"
}
```

3. 点击 Execute
4. 注意：这是 SSE 流式接口，Swagger UI 可能显示完整响应或分块响应

---

## 十、完整测试流程（推荐执行顺序）

```
第 1 步：admin 登录 → 设置 Token
第 2 步：GET  /api/user/info          → 验证登录
第 3 步：POST /api/file/upload        → 上传文件，记录 fileId
第 4 步：GET  /api/file/{fileId}/url  → 验证文件可下载
第 5 步：POST /api/doc                → 创建文档，记录 docId
第 6 步：GET  /api/doc/page           → 查看文档列表
第 7 步：PUT  /api/doc/{docId}/status → 发布文档
第 8 步：POST /api/literature         → 创建文献，记录 litId
第 9 步：GET  /api/literature/{litId} → 查看文献详情，验证 viewCount+1
第 10 步：GET /api/search             → 搜索验证
第 11 步：GET /api/notification/unread-count → 查看通知
第 12 步：POST /api/rag/chat          → AI 问答
第 13 步：guest 登录 → 换 Token → 下载文献 → 验证 403
第 14 步：admin 登录 → 删除文档/文献 → 验证删除成功
```

---

## 测试结果记录表

| 编号 | 接口 | 测试场景 | 实际结果 | 是否通过 | 备注 |
|------|------|----------|----------|----------|------|
| TC-001 | POST /api/user/login | 正常登录 | code=200 | ✅ | |
| TC-002 | POST /api/user/login | 错误密码 | code=500 | ✅ | |
| TC-003 | POST /api/user/login | 空用户名 | code=400 | ✅ | |
| ... | ... | ... | ... | ... | ... |

> 将每个接口的实际返回填入「实际结果」列，与预期对比即可
