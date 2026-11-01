# Kuikly 组件市场上架指引

官方市场（<https://kuikly.tds.qq.com/third-party?tab=component>）的收录方式：向
[Tencent-TDS/KuiklyUI-third-party](https://github.com/Tencent-TDS/KuiklyUI-third-party) 提交 PR，
编辑根目录 `KuiklyUI-Libraries.json` 添加组件条目。

## 前置条件（全部完成后再提 PR）

- [x] 产物已发布 Maven Central（`io.github.zhanghaifei1997:kuiklyCode:0.1.0`，repo1 可下载）
- [x] 源码已推送公开 GitHub 仓库 `zhanghaifei1997/kuikly-code`
- [x] README 完整：功能、API、平台支持、与 QRose 的差异定位
- [x] LICENSE（MIT）+ THIRD_PARTY_NOTICES（qrcode-kotlin 署名）

## 条目（2026-11 提交格式，与 PR #47 一致）

```json
{
  "githubUrl": "https://github.com/zhanghaifei1997/kuikly-code",
  "examples": ["https://github.com/zhanghaifei1997/kuikly-code/tree/main/demo"],
  "componentName": "kuikly-code",
  "componentDescription": "Kuikly 经典 DSL 二维码/条形码渲染组件：纯 Kotlin common Canvas 绘制（QR 引擎为 qrcode-kotlin 移植，Code128 编码表对照 zxing 校验），零原生依赖零模块注册，支持模块形状（方/圆点/圆角）、渐变前景、标准静区、人眼可读文本与响应式 Provider，Android/iOS/鸿蒙/Web 一套代码。",
  "componentType": "KuiklyView",
  "developer": "zhanghaifei1997",
  "Android": true,
  "iOS": true,
  "Ohos": true,
  "Js": true,
  "sDynamic": false
}
```

## 提交流程（沉淀自 PR #47/#50）

1. 浅 clone 上游 → 建分支
2. 文本级在 `]` 前追加条目（勿整体重排 JSON；文件末尾无换行符；`}` 与 `]` 间有换行）
3. push 到自己 fork → `gh pr create`（body 按组件名/类型/功能/平台/产物地址/演示模板）
4. PR body 中注明与 QRose（Compose DSL 适配版）的差异定位：本组件服务经典 DSL 工程

## 上架后的维护

- 版本更新：先发新产物，再提 PR 更新条目
- Kuikly 框架大版本升级后回归验证
