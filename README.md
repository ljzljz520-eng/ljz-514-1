# 🌿 植物园游线规划器

管理员维护园区节点（温室 / 花境 / 湖边栈道 / 休息点）与节点间路段（步行距离、坡度、是否适合推车）；
游客选择起点、终点和游览偏好后，由 **Java 后端** 用「按偏好加权的 Dijkstra 算法」计算最优路线，
前端以 **SVG 园区地图 + 分段文字说明** 展示结果。

## 快速开始

仅需 JDK 17+（零第三方依赖，使用 JDK 内置 HTTP 服务器）：

```bash
./run.sh          # 编译并启动，默认端口 8080
```

打开 http://localhost:8080

- **游客导览**：点击地图节点或下拉框选择起终点 → 选择游览偏好 → 开始规划
- **管理后台**：输入管理口令（默认 `admin123`，可用环境变量 `ADMIN_TOKEN` 覆盖）后维护节点与路段

环境变量：`PORT`（端口）、`ADMIN_TOKEN`（管理口令）、`DATA_FILE`（数据文件，默认 `data/garden.json`）。
首次启动自动生成示例园区数据，之后的增删改会持久化到该文件。

## 游览偏好

| 偏好 | 说明 |
|---|---|
| 最短距离 | 总步行距离最短 |
| 轻松少爬坡 | 坡度越大代价越高，可设「可接受最大坡度」，超限路段直接排除 |
| 推车友好 | 只走标记为适合推车的路段，无可通行路线时给出提示 |
| 观景优先 | 途经温室/花境/湖边栈道的路段代价打折，尽量串联景点 |
| 坡度最小 | 让全程累计爬升最小，距离其次 |

## API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/garden` | 园区全量数据（节点、路段、偏好列表） |
| GET | `/api/route?start=&end=&mode=&maxSlope=` | 规划路线，返回分段说明 |
| POST | `/api/admin/nodes` | 新增节点（需 `X-Admin-Token` 头） |
| PUT / DELETE | `/api/admin/nodes/{id}` | 修改 / 删除节点（删除节点会级联删除相连路段） |
| POST | `/api/admin/edges` | 新增路段（需口令） |
| PUT / DELETE | `/api/admin/edges/{id}` | 修改 / 删除路段 |

示例：

```bash
curl "http://localhost:8080/api/route?start=gate&end=boardwalk&mode=stroller"
curl -X POST http://localhost:8080/api/admin/nodes \
  -H "X-Admin-Token: admin123" -H "Content-Type: application/json" \
  -d '{"name":"杜鹃谷","type":"FLOWER_BORDER","description":"春季杜鹃花海","x":700,"y":200}'
```

## 项目结构

```
src/main/java/com/botanic/garden/
├── Main.java                  # 启动入口
├── http/Json.java             # 极简 JSON 解析/序列化（零依赖）
├── http/WebServer.java        # REST API + 静态资源
├── model/                     # NodeType / GardenNode / Edge / Garden
├── service/RoutePlanner.java  # 按偏好加权的 Dijkstra 路线规划
└── store/DataStore.java       # JSON 文件持久化 + 示例园区
src/main/resources/static/     # 前端单页（index.html / style.css / app.js）
data/garden.json               # 运行时生成的数据文件
```
