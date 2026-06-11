# Lab OA API 测试用例（Apifox）

## 通用信息

- **Base URL**: `http://localhost:8080`
- **Content-Type**: `application/json`（文件上传使用 `multipart/form-data`）
- **认证方式**: `Authorization: {token}`（登录接口返回的 token）
- **默认密码**: `123456`

---

## 1️⃣ 用户认证

### 1.1 用户登录

```
POST /api/user/login
Content-Type: application/json

{
    "username": "admin",
    "password": "123456"
}
```

**预期响应**:
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "token": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
        "user": {
            "id": 1,
            "username": "admin",
            "email": "admin@laboa.com",
            "realName": "系统管理员",
            "roles": ["admin"],
            "status": 1
        }
    }
}
```

### 1.2 用户注册

```
POST /api/user/register
Content-Type: application/json

{
    "username": "testuser",
    "password": "123456",
    "email": "test@laboa.com"
}
```

**预期响应**: `{"code": 200, "message": "success", "data": null}`

### 1.3 获取当前用户信息

```
GET /api/user/info
Authorization: {上一步获取的token}
```

**预期响应**: 返回当前登录的用户信息 UserVO

---

## 2️⃣ 测试账号列表

| 账号 | 密码 | 角色 | 说明 |
|------|------|------|------|
| `admin` | `123456` | 管理员 | 有全部权限 |
| `teacher` | `123456` | 教师 | 除用户管理外都有权限 |
| `student` | `123456` | 学生 | 同教师权限 |
| `guest` | `123456` | 游客 | 仅查看文献 |

> 后续测试请先登录获取 token，填入 Apifox 的全局变量 `{{token}}`

---

## 3️⃣ 用户管理（管理员）

### 3.1 用户分页

```
GET /api/admin/user/page?page=1&size=10&keyword=
Authorization: {{token}}
```

### 3.2 修改用户状态

```
PUT /api/admin/user/{userId}/status?status=0
Authorization: {{token}}
```

- `status=0` 禁用，`status=1` 启用

### 3.3 分配角色

```
PUT /api/admin/user/{userId}/roles
Authorization: {{token}}
Content-Type: application/json

[1, 2]
```

---

## 4️⃣ 角色管理（管理员）

### 4.1 角色列表

```
GET /api/admin/role/list
Authorization: {{token}}
```

### 4.2 创建角色

```
POST /api/admin/role
Authorization: {{token}}
Content-Type: application/json

{
    "roleCode": "visitor",
    "roleName": "访客",
    "description": "仅可查看公开文献"
}
```

### 4.3 删除角色

```
DELETE /api/admin/role/{roleId}
Authorization: {{token}}
```

---

## 5️⃣ 文件上传

### 5.1 上传文件

```
POST /api/file/upload
Authorization: {{token}}
Content-Type: multipart/form-data

file: (选择本地文件)
```

**预期响应**:
```json
{
    "code": 200,
    "data": {
        "id": 123456789,
        "originalName": "document.pdf",
        "storedName": "uuid-filename.pdf",
        "bucket": "lab-oa",
        "fileSize": 102400,
        "mimeType": "application/pdf",
        "md5": "d41d8cd98f00b204e9800998ecf8427e",
        "status": 1
    }
}
```

### 5.2 获取文件预签名URL

```
GET /api/file/{fileId}/url
Authorization: {{token}}
```

**预期响应**: 返回可直接访问的 MinIO 下载链接（1小时内有效）

### 5.3 获取文件信息

```
GET /api/file/{fileId}/info
Authorization: {{token}}
```

---

## 6️⃣ 内部文档

### 6.1 创建文档（上传Markdown/PDF）

```
POST /api/doc
Authorization: {{token}}
Content-Type: multipart/form-data

title: "Spring Boot 开发指南"
file: (选择 .md 或 .pdf 文件)
```

### 6.2 文档分页

```
GET /api/doc/page?page=1&size=10&keyword=Spring
Authorization: {{token}}
```

### 6.3 获取文档详情

```
GET /api/doc/{docId}
Authorization: {{token}}
```

**预期响应**: 返回文档信息 + 文件预签名下载链接

### 6.4 更新文档

```
PUT /api/doc/{docId}
Authorization: {{token}}
Content-Type: multipart/form-data

title: "更新后的标题"
file: (可选，重新上传文件)
```

### 6.5 更新文档状态

```
PUT /api/doc/{docId}/status?status=2
Authorization: {{token}}
```

- `status=1` 草稿, `2` 发布, `3` 归档

### 6.6 删除文档

```
DELETE /api/doc/{docId}
Authorization: {{token}}
```

---

## 7️⃣ 文献管理

### 7.1 创建文献

```
POST /api/literature
Authorization: {{token}}
Content-Type: multipart/form-data

literature: {"title":"论文标题","authors":"作者1,作者2","abstract":"摘要内容","keywords":"关键词1,关键词2","publishYear":2024,"doi":"10.xxxx/xxxxx","permissionLevel":2}
file: (选择 PDF 文件)
```

> 注意：`literature` 部分是 JSON 字符串，`file` 部分是文件。在 Apifox 中，选择 `body` → `form-data`，添加两个字段：`literature`（类型 text）和 `file`（类型 file）。

### 7.2 文献分页

```
GET /api/literature?page=1&size=10&keyword=AI&author=张
Authorization: {{token}}
```

### 7.3 获取文献详情

```
GET /api/literature/{literatureId}
Authorization: {{token}}
```

### 7.4 更新文献

```
PUT /api/literature/{literatureId}
Authorization: {{token}}
Content-Type: application/json

{
    "id": {literatureId},
    "title": "新标题",
    "authors": "作者3",
    "abstract": "新摘要"
}
```

### 7.5 下载文献

```
GET /api/literature/{literatureId}/download
Authorization: {{token}}
```

**预期响应**: 返回文件预签名下载链接
- 使用 `admin/teacher/student` 账号：可以下载
- 使用 `guest` 账号：返回 403 禁止下载

### 7.6 删除文献

```
DELETE /api/literature/{literatureId}
Authorization: {{token}}
```

---

## 8️⃣ 全局搜索

### 8.1 搜索

```
GET /api/search?keyword=Spring&type=all&page=1&size=10
Authorization: {{token}}
```

- `type`: `all`（全文搜索）, `doc`（仅文档）, `literature`（仅文献）
- 结果会高亮匹配的关键词

---

## 9️⃣ 通知

### 9.1 通知列表

```
GET /api/notification?isRead=0&page=1&size=10
Authorization: {{token}}
```

- `isRead=0` 未读, `isRead=1` 已读, 不传则全部

### 9.2 标记已读

```
PUT /api/notification/{notificationId}/read
Authorization: {{token}}
```

### 9.3 未读数量

```
GET /api/notification/unread-count
Authorization: {{token}}
```

---

## 🔟 AI 问答（RAG）

### 10.1 问答

```
POST /api/rag/chat
Authorization: {{token}}
Content-Type: application/json

{
    "question": "实验室有哪些关于机器学习的文献？"
}
```

**预期响应**: SSE (Server-Sent Events) 流式响应，在 Apifox 中可直接看到完整返回内容。

---

## 🔬 Apifox 配置建议

### 1. 环境变量配置

在 Apifox 中创建 `Lab OA` 环境，设置以下变量：

| 变量名 | 初始值 | 说明 |
|--------|--------|------|
| `baseUrl` | `http://localhost:8080` | 后端地址 |
| `token` | (留空) | 登录后自动获取 |

### 2. 前置脚本（自动设置 Token）

在每个需要登录的接口的「前置操作」中添加脚本：

```javascript
// 登录后自动保存 token
const response = pm.response.json();
if (response.code === 200 && response.data.token) {
    pm.environment.set("token", response.data.token);
}
```

### 3. 全局请求头

在「环境配置」→「全局请求头」中添加：

| 名称 | 值 |
|------|-----|
| `Authorization` | `{{token}}` |

### 4. 测试执行顺序建议

```
1. 1.1 用户登录 (admin)       ← 先获取 token
2. 5.1 上传文件               ← 获取文件 ID
3. 6.1 创建文档               ← 使用上传的文件
4. 7.1 创建文献               ← 使用上传的文件
5. 8.1 全局搜索               ← 搜索已有内容
6. 9.1 通知列表               ← 查看通知
7. 10.1 AI问答                ← 测试 RAG
```

---

## ⚠️ 注意事项

1. **首次启动**：ES 索引需要手动创建或等待首次文档插入时自动创建
2. **文件类型**：支持 PDF、Word、Markdown、图片等常见格式
3. **文件大小限制**：默认单文件最大 100MB，总请求最大 200MB（在 `application.yml` 中配置）
4. **Token 有效期**：24 小时（Sa-Token 配置），过期需重新登录
5. **下载权限**：
   - 管理员/教师/学生：可以下载文献
   - 游客：仅可在线查看，下载会返回错误
