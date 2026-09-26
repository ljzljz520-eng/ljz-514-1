# 🌿 植物园游线规划器

管理员维护 **温室、花境、湖边栈道、休息点、入口** 等节点，以及节点之间步道的
**步行距离、坡度、是否适合推车、景观评分**；游客选择起点、终点和游览偏好后，
后端用 **Java + Dijkstra 最短路径算法** 计算路线，前端展示分段步行说明并在地图上高亮。

## 技术栈

- 后端：纯 Java（JDK 内置 `com.sun.net.httpserver.HttpServer`），**零第三方依赖、无需 Maven**
- 算法：Dijkstra 单源最短路径，权重函数随偏好变化
- 前端：原生 HTML / CSS / JavaScript 单页，内嵌 SVG 园区地图
- 存储：JSON 文件持久化（`data/garden.json`），启动时不存在则写入内置示例园区

## 运行

需要 JDK 17+（推荐 21）。

```bash
./build.sh          # 编译到 out/
./run.sh            # 启动，默认端口 8080
# 或手动：
# java -cp out server.HttpServerMain
```

浏览器打开 <http://localhost:8080/>。

环境变量（均可选）：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PORT` | `8080` | 服务端口 |
| `ADMIN_TOKEN` | `garden-admin` | 管理接口口令（请求头 `X-Admin-Token`） |
| `DATA_FILE` | `data/garden.json` | 园区数据文件 |
| `WEB_DIR` | `web` | 前端静态资源目录 |

## 游览偏好与权重

| 偏好 | 权重（每条步道） | 说明 |
| --- | --- | --- |
| 距离最短 | 距离(米) | 标准最短路 |
| 坡度最缓 | 距离 × (1 + 8% × 上坡坡度) | 陡坡代价增大，倾向缓坡绕行 |
| 推车友好 | 仅允许 `strollerFriendly=true` 的步道；同类中优先短而平 | 勾选“携带推车”时所有偏好都受此限制 |
| 观景最佳 | 距离 ÷ (0.5 + 景观分/10)，陡坡再加 30% | 景观分 0-10，高景观路段“缩短”感知距离 |

输出包含：总距离、按 65 米/分钟估算的步行时间、累计爬升、
八方位方向（东/东南/南…）、每段中文分步说明、陡坡与推车提示。

## HTTP 接口

| 方法 | 路径 | 需管理员 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/nodes` | 否 | 节点列表 |
| POST | `/api/nodes` | 是 | 新增节点 |
| PUT | `/api/nodes/{id}` | 是 | 修改节点 |
| DELETE | `/api/nodes/{id}` | 是 | 删除节点（连带删除相连步道） |
| GET | `/api/edges` | 否 | 步道列表 |
| POST | `/api/edges` | 是 | 新增步道 |
| PUT | `/api/edges/{id}` | 是 | 修改步道 |
| DELETE | `/api/edges/{id}` | 是 | 删除步道 |
| POST | `/api/plan` | 否 | 规划路线，body：`startId, goalId, preference, withStroller` |
| POST | `/api/admin/reset` | 是 | 恢复内置示例园区 |
| GET | `/api/health` | 否 | 健康检查 |

管理接口需在请求头携带 `X-Admin-Token: garden-admin`。

### 规划请求示例

```bash
curl -X POST http://localhost:8080/api/plan \
  -H 'Content-Type: application/json' \
  -d '{"startId":"e1","goalId":"bw1","preference":"scenic","withStroller":false}'
```

## 目录结构

```
botanical-planner/
├── src/
│   ├── engine/        # 领域模型 + Dijkstra 规划核心
│   │   ├── GardenNode / NodeType / Edge / GardenGraph
│   │   ├── Preference / Segment / RouteResult
│   │   ├── GardenStore.java      # 数据仓库 + 默认园区 + JSON 持久化
│   │   └── RoutePlanner.java     # Dijkstra
│   └── server/
│       ├── Json.java             # 零依赖 JSON 解析/序列化
│       └── HttpServerMain.java   # HTTP 服务与 REST 接口
├── web/index.html               # 前端单页
├── data/garden.json             # 运行后自动生成
├── build.sh / run.sh
└── README.md
```
