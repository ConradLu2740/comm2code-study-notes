# 短链接后台 (shortlink-admin)

Vue 3 + Element Plus 写的短链接后台管理界面。

## 技术栈

- **Vue 3.4** - Composition API
- **Vite 5** - 构建工具
- **Element Plus 2.7** - UI 组件库
- **Vue Router 4** - 路由
- **Pinia 2** - 状态管理（比 Vuex 轻量）
- **Axios** - HTTP 客户端
- **ECharts 5** - 统计图表

## 启动

### 1. 安装依赖

```bash
cd admin
npm install
```

### 2. 启动后端

确保 Spring Boot 项目在 `http://localhost:8080` 运行：

```bash
cd ..
mvn spring-boot:run
```

### 3. 启动前端

```bash
npm run dev
```

访问 `http://localhost:5173`。

## 目录结构

```
admin/
├── package.json
├── vite.config.js          # /api 代理到 8080
├── index.html
├── src/
│   ├── main.js             # 应用入口
│   ├── App.vue             # 根组件
│   ├── style.css           # 全局样式
│   ├── api/
│   │   └── index.js        # Axios 实例 + 拦截器
│   ├── router/
│   │   └── index.js        # 路由 + 导航守卫
│   ├── stores/
│   │   └── user.js         # 用户状态（token + info）
│   ├── components/
│   │   └── Layout.vue      # 侧边栏 + 顶栏布局
│   ├── views/
│   │   ├── Login.vue       # 登录页
│   │   ├── Dashboard.vue   # 仪表盘
│   │   ├── ShortLinks.vue  # 短链管理
│   │   ├── Domains.vue     # 域名管理
│   │   ├── Stats.vue       # 访问统计
│   │   └── Users.vue       # 用户信息
│   └── utils/
│       ├── format.js       # 格式化工具
│       └── storage.js      # localStorage 封装
```

## 教学点

1. **路由守卫** - `router/index.js` 检查 token，未登录跳 `/login`
2. **Pinia 状态管理** - `stores/user.js` 管理 token 和用户信息
3. **Axios 拦截器** - `api/index.js` 自动加 `Authorization` 头、统一处理 401
4. **组件化** - Layout 组件复用侧边栏 + 顶栏

## 后端 API 对接

| 前端调用 | 后端接口 |
|---------|---------|
| `POST /api/v1/auth/login` | 登录返回 JWT |
| `POST /api/v1/shortlinks` | 创建短链 |
| `GET /api/v1/shortlinks/{code}` | 查询短链详情 |
| `GET /api/v1/shortlinks/{code}/stats` | 短链统计 |
| `POST /api/v1/domains?userId=N` | 绑定域名 |
| `GET /api/v1/domains?userId=N` | 查询我的域名 |
| `GET /api/v1/users/me` | 当前用户信息 |

> 注：后端没有"列出我的所有短链"接口，前端用 localStorage 缓存已创建的短码。

## 截图说明

启动后默认进入 `/login` 页面：

- 输入用户名密码登录（先用后端 `/api/v1/auth/register` 注册一个）
- 登录后跳到 `/`（Dashboard），展示总链接数 / 总点击数 / 今日新增 / Top 5 饼图
- 左侧导航：
  - **仪表盘** `/` - 聚合统计
  - **短链管理** `/shortlinks` - 创建 / 复制 / 查询
  - **域名管理** `/domains` - 绑定自定义域名
  - **访问统计** `/stats` - 选短码看折线图 + 设备/国家饼图
  - **个人信息** `/users` - 当前登录用户信息

## 构建生产版本

```bash
npm run build
```

产物在 `admin/dist/`，交给 nginx 静态托管即可。