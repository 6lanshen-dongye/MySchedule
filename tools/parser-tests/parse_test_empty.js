// 用 jsdom 在 Node 里跑 APP 里那段注入脚本,验证课表解析逻辑
const fs = require('fs');
const { JSDOM } = require('jsdom');

const ktPath = 'D:\\CourseScheduleApp\\MySchedule\\app\\src\\main\\java\\com\\myschedule\\app\\ui\\imports\\ScrapeScript.kt';
const kt = fs.readFileSync(ktPath, 'utf8');
const startMark = 'internal const val SCRAPE_JS = """';
const s = kt.indexOf(startMark);
if (s < 0) throw new Error('SCRAPE_JS not found');
const bodyStart = s + startMark.length;
const e = kt.indexOf('"""', bodyStart);
let js = kt.substring(bodyStart, e).split("${'$'}").join('$');

const html = fs.readFileSync('D:\\CourseScheduleApp\\kb_empty_test.html', 'utf8');
const dom = new JSDOM(html, { runScripts: 'outside-only' });
global.window = dom.window;
global.document = dom.window.document;

const result = dom.window.eval(js);
const data = JSON.parse(result);
console.log('ok =', data.ok, ' blocks =', data.blocks.length, ' times =', (data.times || []).filter(Boolean).length);
console.log('--- 解析出的课程块 ---');
for (const b of data.blocks) {
  console.log(`周${b.day} ${b.start}-${b.end}节 | ${b.name} | 老师:${b.teacher || '-'} | 地点:${b.location || '-'} | 周次:${b.weeks || '-'}`);
}
console.log('--- 作息时间 ---');
console.log((data.times || []).map((t, i) => `第${i + 1}节 ${t || '-'}`).join('  '));

// 顺便按 Kotlin 侧同样的规则合并课程,看看最终会有几门课
const groups = new Map();
for (const b of data.blocks) {
  const key = [b.name.trim(), b.teacher.trim(), b.location.trim()].join('|');
  if (!groups.has(key)) groups.set(key, []);
  groups.get(key).push(b);
}
console.log(`--- 合并后课程数 = ${groups.size} ---`);
for (const [k, list] of groups) {
  const sessions = list.map(b => `周${b.day} ${b.start}-${b.end}节`).join(' / ');
  console.log(`${k.split('|')[0]}  →  ${sessions}`);
}
