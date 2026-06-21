# REST API 文档

> Base URL: `http://localhost:8080`
> Content-Type: `application/json`

## 1. 创建短链接

**`POST /api/v1/shortlinks`**

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `longUrl` | string | ✅ | 长链接，必须以 http:// 或 https:// 开头，最长 2048 字符 |
| `alias` | string | ❌ | 自定义短码，3-16 位字母数字下划线连字符 |
| `expireAt` | string (ISO-8601) | ❌ | 过期时间，如 `2027-12-31T23:59:59Z` |

### 示例

```bash
curl -X POST http://localhost:8080/api/v1/shortlinks \
  -H "Content-Type: application/json" \
  -d '{
    "longUrl": "https://github.com/ConradLu2740/comm2code-study-notes",
    "alias": "mystudy"
  }'
```

### 响应（201 Created）

```json
{
  "shortCode": "mystudy",
  "shortUrl": "http://localhost:8080/mystudy",
  "longUrl": "https://github.com/ConradLu2740/comm2code-study-notes",
  "createdAt": "2026-06-21T10:30:00Z",
  "clickCount": 0
}
```

### 错误响应

**400 Bad Request** - 参数校验失败：
```json
{
  "timestamp": "2026-06-21T10:30:00Z",
  "status": 400,
  "error": "Validation Failed",
  "message": "请求参数校验失败",
  "path": "/api/v1/shortlinks",
  "details": [
    "longUrl: 长链接必须以 http:// 或 https:// 开头"
  ]
}
```

**429 Too Many Requests** - 限流：
```json
{
  "timestamp": "2026-06-21T10:30:00Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "请求过于频繁，请稍后再试",
  "path": "/api/v1/shortlinks"
}
```

---

## 2. 短链重定向

**`GET /{shortCode}`**

302 重定向到对应的长链接，并自动 +1 点击数。

### 示例

```bash
curl -i http://localhost:8080/mystudy
```

### 响应（302 Found）

```
HTTP/1.1 302 Found
Location: https://github.com/ConradLu2740/comm2code-study-notes
```

### 错误响应

**404 Not Found** - 短链不存在或已过期：
```json
{
  "timestamp": "2026-06-21T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "短链接不存在或已过期: xxx",
  "path": "/xxx"
}
```

---

## 3. 获取短链接详情

**`GET /api/v1/shortlinks/{shortCode}`**

### 示例

```bash
curl http://localhost:8080/api/v1/shortlinks/mystudy
```

### 响应（200 OK）

```json
{
  "shortCode": "mystudy",
  "shortUrl": "http://localhost:8080/mystudy",
  "longUrl": "https://github.com/ConradLu2740/comm2code-study-notes",
  "createdAt": "2026-06-21T10:30:00Z",
  "expireAt": null,
  "clickCount": 42
}
```

---

## 4. H2 控制台（仅开发环境）

**`GET /h2-console`**

浏览器访问，JDBC URL 填 `jdbc:h2:mem:shortlink`，用户名 `sa`，密码空。

可以可视化看 `short_link` 表的数据。

---

## 状态码说明

| 状态码 | 含义 | 触发条件 |
|---|---|---|
| 200 | OK | 查询成功 |
| 201 | Created | 创建成功 |
| 302 | Found | 短链重定向 |
| 400 | Bad Request | 参数校验失败、自定义短码已被占用 |
| 404 | Not Found | 短链不存在或已过期 |
| 429 | Too Many Requests | 触发限流 |
| 500 | Internal Server Error | 系统异常 |

## 限流规则

- 按客户端 IP 限流
- 默认：每秒 5 个令牌，桶容量 10
- 超出会返回 429

---

最后更新：2026-06-21