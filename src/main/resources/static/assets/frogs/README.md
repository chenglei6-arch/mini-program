# 林蛙运行时素材

这里只放小程序运行时要直接读取的文件，设计源文件在项目根目录 `design-sources/`（不参与运行时发布）。

## 当前内容

- 九张 `*.png`：角色墙与详情页用的预览图，1024x1024 透明 PNG，按有效 Alpha 内容边界归一化留白。
- `GET /v1/content/frogs` 返回稳定的 `assetUrl`（相对路径 `/assets/frogs/{id}.png`），由小程序端拼接域名。

## 已移出运行时

- `sources/frogs/*.docx`：九份纹样设计说明，内容已随 `content/frog-details.json` 进接口，DOCX 原件保留在 `design-sources/`。
- `components/`：指尖剪林蛙（点击式拼装）的 30 张部件图，共 24MB。该游戏当前未上架（`content_game` 无 `paper-cutting` 行、页面未登记到 `app.json`），运行时不再随包发布。
  恢复上架时按 `frog-components.json` 的 `assetUrl` 命名，从设计源文件重新拷回：

  ```bash
  # 文件名映射见 frog-components.json；示例：
  cp "design-sources/frogs/forest-护林蛙/护林蛙部件/护林蛙主体.png" static/assets/frogs/components/forest-main.png
  ```

  30 张部件图与设计源文件均为字节一致，逐张按上表拷回即可。
