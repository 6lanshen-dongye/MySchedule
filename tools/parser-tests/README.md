# 解析回归测试

这些用例把 `ScrapeScript.kt` 里注入页面执行的 JS 抽出来，在 Node + jsdom 上跑，
用来验证「正方教务系统 → 课表 JSON」的解析在各种页面版式下都正确。

## 跑起来

```bash
npm i jsdom        # 仅首次
node parse_test_zf52.js
```

每个用例输出类似：

```
ok = true  blocks = 6  times = 0
```

- `blocks`：解析出的课程块数量
- `times`：解析出的作息时间数量（很多页面不含时间，为 0 属正常）

## 用例覆盖的版式

| 脚本 | 页面版式 |
|---|---|
| `parse_test.js` | 标准正方课表（带字体图标字段） |
| `parse_test_zf.js` / `parse_test_zf52.js` | 正方不同大版本的表结构 |
| `parse_test_list.js` / `parse_test_lv.js` | 列表视图（`kblist_table`） |
| `parse_test_info.js` | 无字段标签、靠文本行识别 |
| `parse_test_multiline.js` | 单元格内多行课程 |
| `parse_test_runon.js` | 课程名与周次/地点挤在一行的 run-on 文本 |
| `parse_test_empty.js` | 空课表（应返回 0 且给出可读诊断信息） |

## 加新用例

把出问题的课表页面另存为 `kb_xxx_test.html`，照着 `parse_test_empty.js` 复制一份改路径即可。
提交前请确认样例里**不含真实的个人信息**。