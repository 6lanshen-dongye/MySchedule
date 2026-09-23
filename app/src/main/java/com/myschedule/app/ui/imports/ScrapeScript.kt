package com.myschedule.app.ui.imports

/** 注入教务系统页面执行的抓取脚本(兼容正方/URP/强智等课表表格) */
internal const val SCRAPE_JS = """(function () {
 try {
  var DAYMAP = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7, '天': 7 };
  var ENMAP = { mon: 1, tue: 2, wed: 3, thu: 4, fri: 5, sat: 6, sun: 7 };

  function norm(s) { return (s || '').replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim(); }
  function textOf(el) { return el ? (el.innerText || el.textContent || '') : ''; }

  /**
   * 把一个课程单元格拆成"行"。
   * 不再做 innerHTML → 游离 div → textContent 的往返:正方那套嵌套结构在这种往返下会丢字。
   * 直接遍历真实 DOM 的文本节点,遇到 <hr> 分块、<br> 换行。
   */
  function blocksOf(cell) {
    var blocks = [], cur = [];
    function flush() { if (cur.length) { blocks.push(cur); cur = []; } }
    function walk(node) {
      var kids = node.childNodes || [];
      for (var i = 0; i < kids.length; i++) {
        var n = kids[i];
        if (n.nodeType === 3) {
          var t = norm(n.nodeValue);
          if (t) cur.push(t);
        } else if (n.nodeType === 1) {
          var tag = (n.tagName || '').toLowerCase();
          if (tag === 'br') continue;
          if (tag === 'hr') { flush(); continue; }
          if (tag === 'script' || tag === 'style' || tag === 'noscript' || tag === 'template') continue;
          walk(n);
        }
      }
    }
    walk(cell);
    flush();
    /* 极端兜底:真取不到就用单元格自己的 textContent 当一行 */
    if (!blocks.length) {
      var raw = norm(cell.textContent || '');
      if (raw) blocks.push([raw]);
    }
    return blocks;
  }

  function dayOf(txt) {
    var t = (txt || '').replace(/\s/g, '');
    var m = t.match(/(?:星期|周)([一二三四五六日天])/);
    if (m) return DAYMAP[m[1]] || 0;
    var e = t.match(/(Mon|Tue|Wed|Thu|Fri|Sat|Sun)/i);
    if (e) return ENMAP[e[1].toLowerCase().substring(0, 3)] || 0;
    return 0;
  }

  function cnNum(s) {
    var map = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '七': 7, '八': 8, '九': 9, '十': 10 };
    s = (s || '').replace(/[第节\s]/g, '');
    if (/^\d+${'$'}/.test(s)) return parseInt(s, 10);
    if (s === '十') return 10;
    if (s.length === 1) return map[s] || 0;
    if (s.charAt(0) === '十') return 10 + (map[s.charAt(1)] || 0);
    if (s.charAt(1) === '十') {
      var v = (map[s.charAt(0)] || 0) * 10;
      if (s.length > 2) v += (map[s.charAt(2)] || 0);
      return v;
    }
    return 0;
  }

  function periodRange(txt) {
    var t = (txt || '').replace(/\s/g, '');
    var m = t.match(/(\d+)[-~至到](\d+)节/);
    if (m) return [parseInt(m[1], 10), parseInt(m[2], 10)];
    m = t.match(/第([一二三四五六七八九十]+)[-~至到]第?([一二三四五六七八九十]+)节/);
    if (m) return [cnNum(m[1]), cnNum(m[2])];
    m = t.match(/第([一二三四五六七八九十]+)节/);
    if (m) { var n = cnNum(m[1]); if (n) return [n, n]; }
    /* 先排除时间:否则 "08:30-09:15" 里的 "30-09" 会被当成节次区间 */
    if (/\d{1,2}:\d{2}/.test(t)) return [0, 0];
    m = t.match(/(\d+)[-~](\d+)/);
    if (m) return [parseInt(m[1], 10), parseInt(m[2], 10)];
    m = t.match(/(\d+)/);
    if (m) return [parseInt(m[1], 10), parseInt(m[1], 10)];
    return [0, 0];
  }

  function timeRange(txt) {
    var m = (txt || '').match(/(\d{1,2}:\d{2})\s*[-~至]\s*(\d{1,2}:\d{2})/);
    return m ? (m[1] + '-' + m[2]) : '';
  }

  function allTables() {
    var out = [].slice.call(document.querySelectorAll('table'));
    var frames = document.querySelectorAll('iframe,frame');
    for (var i = 0; i < frames.length; i++) {
      try {
        var d = frames[i].contentDocument;
        if (d) out = out.concat([].slice.call(d.querySelectorAll('table')));
      } catch (e) { }
    }
    return out;
  }

  /* 按 rowspan/colspan 把表格还原成二维网格,同一格在它覆盖的每一行都指向同一个 DOM 节点 */
  function expand(table) {
    var rows = [].slice.call(table.rows);
    var grid = [];
    for (var r = 0; r < rows.length; r++) {
      if (!grid[r]) grid[r] = [];
      var cells = [].slice.call(rows[r].cells);
      var c = 0;
      for (var i = 0; i < cells.length; i++) {
        while (grid[r][c] !== undefined) c++;
        var cell = cells[i];
        var rs = cell.rowSpan || 1, cs = cell.colSpan || 1;
        for (var dr = 0; dr < rs; dr++) {
          var rr = r + dr;
          if (!grid[rr]) grid[rr] = [];
          for (var dc = 0; dc < cs; dc++) grid[rr][c + dc] = cell;
        }
        c += cs;
      }
    }
    return grid;
  }

  /**
   * 找周次文本。课程名末尾的数字很容易被吞进来:
   * "电工电子技术2" + "1-4周" 会被匹配成 "21-4周",所以要求区间"起始≤结束且≤30",
   * 不合法就往后挪一个字符重试。
   */
  function validWeeks(s) {
    var re = /([0-9]+)[-~]([0-9]+)/g, m;
    while ((m = re.exec(s)) !== null) {
      var a = parseInt(m[1], 10), b = parseInt(m[2], 10);
      if (a > b || a > 30 || b > 30) return false;
    }
    return true;
  }

  function findWeeks(t) {
    var re = /[0-9]+(?:[-~][0-9]+)?周(?:[,，、][0-9]+(?:[-~][0-9]+)?周)*/g;
    var m;
    while ((m = re.exec(t)) !== null) {
      if (validWeeks(m[0])) return { index: m.index, text: m[0] };
    }
    return null;
  }

  /** 教师名可能和地点尾字粘在一起(如"运动场朱婷"),把地点特征字削掉 */
  function trimTeacher(t) {
    var s = t || '';
    while (s.length > 2 && /[场楼室馆区科教阶部校院园心道路栋舍]/ .test(s.charAt(0))) {
      s = s.substring(1);
    }
    return s;
  }

  /**
   * 把一个课程块的多行文字归位成字段。
   * 正方 jwglxt 的行序大致是:课程名(1-2节) / 周次 / 地点 / 教师 / 教学班 / 讲课:32,实验:12…
   * 这里按"像什么"判断,不依赖固定行号。
   */
  function analyzeLines(lines) {
    if (!lines || !lines.length) return null;
    var nameFull = lines[0];
    var periods = '';
    var pm = nameFull.match(/[（(]\s*([0-9]+)\s*[-~至]\s*([0-9]+)\s*节\s*[)）]/);
    if (pm) periods = pm[1] + '-' + pm[2];
    else {
      pm = nameFull.match(/[（(]\s*第?\s*([0-9]+)\s*节\s*[)）]/);
      if (pm) periods = pm[1];
    }
    var name = norm(nameFull.replace(/[（(][^)）]{0,14}节[)）]/g, ' ')).replace(/[（(]\s*[)）]/g, '').trim();
    if (!name) name = nameFull;

    var weeks = '', location = '', teacher = '';
    for (var i = 1; i < lines.length; i++) {
      var L = lines[i];
      if (!L) continue;
      /* 行首是"教师名(2026-…教学班…)"粘在一行的:先摘出教师 */
      var lead = L.match(/^([\u4e00-\u9fa5]{2,4})\s*[（(]\s*[0-9]{4}\s*[-–—]/);
      if (lead) {
        if (!teacher) teacher = trimTeacher(lead[1]);
        continue;
      }
      /* 形如 "1-4" / "第1-2节" 的单行是节次;形如 "08:30-09:15" 的是时间;纯数字行(学时/人数)忽略 */
      var bare = L.replace(/\s/g, '');
      if (/^[0-9]{1,2}:[0-9]{2}/.test(bare)) continue;
      if (/^[0-9]{1,3}${'$'}/.test(bare)) continue;
      var pmLine = bare.match(/^第?([0-9]{1,2})[-~至]([0-9]{1,2})节?${'$'}/);
      if (!pmLine) {
        var pmSingle = bare.match(/^第([0-9]{1,2})节${'$'}/);
        if (pmSingle) pmLine = [null, pmSingle[1], null];
      }
      if (pmLine) {
        if (!periods) periods = pmLine[2] ? (pmLine[1] + '-' + pmLine[2]) : pmLine[1];
        continue;
      }
      if (/讲课\s*[:：]|实验\s*[:：]|学分|必修|限选|任选|实践\s*[:：]/.test(L)) continue;   /* 教学班统计行 */
      if (/^[（(]?\s*\d{4}\s*[-–—]/.test(L)) continue;                                     /* (2026-2027-1)-... */
      if (/班/.test(L)) continue;                                                           /* 教学班 */
      if (!weeks && /[0-9]\s*[-~,，]?\s*[0-9]*\s*周/.test(L)) { weeks = L; continue; }
      if (/^\(?\s*第?\s*[0-9]*\s*[-~]?\s*[0-9]*\s*节\s*\)?${'$'}/.test(L.replace(/\s/g, ''))) continue;
      if (!location && /[0-9]|楼|室|馆|场|区|科|教|阶|机|体/.test(L)) { location = L; continue; }
      if (!teacher) { teacher = L; continue; }
    }

    /* 有的系统整格塞成一行(没有换行):按关键词把字段切出来 */
    if (lines.length <= 1 || name.length > 18 || /周/.test(name)) {
      var full = lines.join(' ');
      var wHit = findWeeks(full);
      if (!weeks && wHit) weeks = wHit.text;
      /* 教师:紧跟在 "(2026-" 教学班号前面的 2-4 个汉字 */
      if (!teacher) {
        var head2 = full.split(/[（(]\s*[0-9]{4}\s*[-–—]/)[0];
        var tail2 = head2.match(/([\u4e00-\u9fa5]{2,3})[\s,，、]*${'$'}/);
        if (tail2) teacher = trimTeacher(tail2[1]);
      }
      if (!location) {
        var lm = full.match(/((?:本部|东区|西区|南区|北区|校区|南湖|阳光|嘉鱼)[\u4e00-\u9fa5A-Za-z0-9\-]*)/);
        if (lm) {
          var loc = lm[1];
          if (teacher && loc.length > teacher.length && loc.lastIndexOf(teacher) === loc.length - teacher.length) {
            loc = loc.substring(0, loc.length - teacher.length);
          }
          location = loc;
        }
      }
      /* 课程名截到第一个"字段特征"之前(周次位置以校验通过的那个为准) */
      var cut = (wHit ? wHit.index : -1);
      if (cut <= 1) {
        cut = full.search(/[0-9]+\s*[-~,，]?\s*[0-9]*\s*周|(?:本部|东区|西区|南区|北区|校区)|[（(]\s*[0-9]{4}\s*[-–—]/);
      }
      if (cut > 1) {
        var nm = norm(full.substring(0, cut).replace(/[（(][^)）]{0,14}节[)）]/g, ' '));
        if (nm.length >= 2) name = nm;
      }
    }
    return { name: name, periods: periods, weeks: weeks, location: location, teacher: teacher };
  }

  /* 去标签取纯文本 */
  function stripTags(s) {
    return (s || '').replace(/<br\s*\/?>/gi, ' ').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');
  }

  /** 一格可能塞多门课(<hr> 或并列的 kbcontent div 分隔) */
  function splitCourseChunks(html) {
    var parts = (html || '').split(/<hr[^>]*>/i);
    var res = [];
    for (var i = 0; i < parts.length; i++) {
      var divs = parts[i].split(/<div[^>]*class\s*=\s*["'][^"']*kbcontent[^"']*["'][^>]*>/i);
      for (var j = 0; j < divs.length; j++) {
        if (norm(stripTags(divs[j]))) res.push(divs[j]);
      }
    }
    return res.length ? res : [html || ''];
  }

  function parseOneLabeled(html) {
    var res = { name: '', weeks: '', location: '', teacher: '', periods: '' };
    var firstFont = html.search(/<font[^>]*title/i);
    var head = firstFont > 0 ? html.substring(0, firstFont) : html;
    res.name = norm(stripTags(head));
    var re = /<font[^>]*title\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)<\/font>/gi;
    var m;
    while ((m = re.exec(html)) !== null) {
      var label = m[1], val = norm(stripTags(m[2]));
      if (!val) continue;
      if (/周次|周数/.test(label)) { if (!res.weeks) res.weeks = val; }
      else if (/地点|教室|场地|校区/.test(label)) { if (!res.location) res.location = val; }
      else if (/教师|老师|授课/.test(label)) { if (!res.teacher) res.teacher = val; }
      else if (/节次|时间/.test(label)) { if (!res.periods) res.periods = val; }
    }
    var pm = res.name.match(/[（(]\s*([0-9]+)\s*[-~至]\s*([0-9]+)\s*节\s*[)）]/);
    if (pm) {
      res.periods = pm[1] + '-' + pm[2];
      res.name = norm(res.name.replace(/[（(][^)）]*节[)）]/g, ' '));
    } else {
      pm = res.name.match(/[（(]\s*第?\s*([0-9]+)\s*节\s*[)）]/);
      if (pm) {
        res.periods = pm[1];
        res.name = norm(res.name.replace(/[（(][^)）]*节[)）]/g, ' '));
      }
    }
    return res;
  }

  function parseLabeledCourses(cell) {
    var chunks = splitCourseChunks(cell.innerHTML || '');
    var res = [];
    for (var i = 0; i < chunks.length; i++) {
      var one = parseOneLabeled(chunks[i]);
      if (one.name) res.push(one);
    }
    return res;
  }

  /* 列表式课表:表头里有「课程名称 / 上课时间 / 周次 / 地点」这类列 */
  function parseListTables() {
    var res = [];
    var list = allTables();
    for (var ti = 0; ti < list.length; ti++) {
      var rows = [].slice.call(list[ti].rows);
      if (rows.length < 2) continue;
      var cols = null, hIdx = -1;
      for (var i = 0; i < Math.min(rows.length, 4) && !cols; i++) {
        var cells = [].slice.call(rows[i].cells);
        var head = cells.map(function (c) { return norm(textOf(c)); }).join(' ');
        if (!/课程|科目/.test(head)) continue;
        if (!/时间|节次|星期|周次/.test(head)) continue;
        var m = {}, j;
        for (j = 0; j < cells.length; j++) {
          var t = norm(textOf(cells[j]));
          if (m.name === undefined && /课程|科目|名称/.test(t)) m.name = j;
          else if (m.teacher === undefined && /教师|老师|授课/.test(t)) m.teacher = j;
          else if (m.place === undefined && /地点|教室|场地/.test(t)) m.place = j;
          else if (m.weeks === undefined && /周次|周数|起止周/.test(t)) m.weeks = j;
          else if (m.time === undefined && /时间|节次|星期|上课/.test(t)) m.time = j;
        }
        if (m.name !== undefined && (m.time !== undefined || m.weeks !== undefined)) {
          cols = m;
          hIdx = i;
        }
      }
      if (!cols) continue;
      for (var r2 = hIdx + 1; r2 < rows.length; r2++) {
        var cs = [].slice.call(rows[r2].cells);
        if (!cs.length) continue;
        var cellAt = function (k) {
          var idx = cols[k];
          return (idx !== undefined && cs[idx]) ? norm(textOf(cs[idx])) : '';
        };
        var name2 = cellAt('name');
        if (!name2 || /^(课程|科目|名称)${'$'}/.test(name2)) continue;
        var timeText = cellAt('time');
        var weekText = cellAt('weeks');
        var day2 = dayOf(timeText) || dayOf(weekText);
        var pr2 = periodRange(timeText);
        if (!pr2[0]) pr2 = periodRange(weekText);
        if (!day2 || !pr2[0]) continue;
        res.push({
          day: day2,
          start: pr2[0],
          end: (pr2[1] && pr2[1] >= pr2[0]) ? pr2[1] : pr2[0],
          name: name2,
          teacher: cellAt('teacher'),
          weeks: weekText || timeText,
          location: cellAt('place')
        });
      }
    }
    return res;
  }

  /* 「星期 | 节次 | 课表信息」式列表:一格一段富文本(周数 / 地点 / 教师 / 教学班) */
  function parseInfoTables() {
    var res = [];
    var list = allTables();
    for (var ti = 0; ti < list.length; ti++) {
      var tb = list[ti];
      var rawRows = [].slice.call(tb.rows);
      if (rawRows.length < 2) continue;
      var hIdx = -1;
      for (var i = 0; i < Math.min(rawRows.length, 4) && hIdx < 0; i++) {
        var head = [].slice.call(rawRows[i].cells).map(function (c) { return norm(textOf(c)); }).join(' ');
        if (/星期/.test(head) && /节次/.test(head)) hIdx = i;
      }
      if (hIdx < 0) continue;
      var grid2 = expand(tb);
      for (var r = hIdx + 1; r < grid2.length; r++) {
        var cells = grid2[r] || [];
        if (!cells.length) continue;
        /* 信息格固定是本行最后一格,且必须有实际内容 */
        var infoCell = cells[cells.length - 1];
        if (!infoCell) continue;
        var infoText = norm(textOf(infoCell));
        if (infoText.length < 3) continue;

        var flat = [];
        var parts = blocksOf(infoCell);
        for (var pi = 0; pi < parts.length; pi++) {
          for (var pj = 0; pj < parts[pi].length; pj++) flat.push(parts[pi][pj]);
        }
        var full = flat.join(' ');
        if (/^(星期|节次|课表信息)/.test(full)) continue;

        var name = '';
        for (var li = 0; li < flat.length; li++) {
          var s = flat[li];
          if (/周数|周次|校区|地点|教室|教师|老师|教学班|节次|星期|学分|备注/.test(s)) continue;
          if (s.length >= 2) { name = s; break; }
        }
        /* 整格挤成一行时:把课程名截到第一个字段标签之前 */
        if (!name) {
          var cutName = full.search(/周数|周次|上课地点|地点|教室|场地|教师|老师|教学班|校区|学分/);
          if (cutName > 1) name = norm(full.substring(0, cutName));
        }
        if (!name || name.length < 2) continue;
        if (/^[0-9\-~,，、\s]+${'$'}/.test(name)) continue;          /* 纯节次/数字,不是课程 */
        if (/周数|周次|校区|地点|教室|教师|老师|教学班/.test(name)) continue;

        /* 字段抓取:用前瞻在下一个标签处截断,避免把"教学班"等粘进来 */
        var grabWeeks = full.match(/(?:周数|周次)\s*[：:]\s*(.+?)(?=校区|上课地点|地点|教师|老师|教学班|$)/);
        var grabPlace = full.match(/(?:上课地点|地点|教室|场地)\s*[：:]\s*(.+?)(?=教师|老师|教学班|校区|周数|周次|$)/);
        var grabTeacher = full.match(/(?:授课教师|教师|老师)\s*[：:]\s*(.+?)(?=教学班|校区|上课地点|地点|周数|周次|$)/);

        var day = dayOf(textOf(cells[0]));
        if (!day) day = dayOf(full);
        var pr = periodRange(textOf(cells[1] || ''));
        if (!pr[0]) pr = periodRange(full);
        if (!day || !pr[0]) {
          miss.push((day ? '周' + day : '无星期') + '/' + (pr[0] ? pr[0] + '-' + pr[1] + '节' : '无节次') +
            ' ' + name.substring(0, 12));
          continue;
        }
        res.push({
          day: day,
          start: pr[0],
          end: (pr[1] && pr[1] >= pr[0]) ? pr[1] : pr[0],
          name: name,
          teacher: grabTeacher ? norm(grabTeacher[1]) : '',
          weeks: grabWeeks ? norm(grabWeeks[1]) : '',
          location: grabPlace ? norm(grabPlace[1]) : ''
        });
      }
    }
    return res;
  }

  var tables = allTables();
  var best = null, bestMap = null, bestHeader = -1, bestScore = -1;
  for (var ti = 0; ti < tables.length; ti++) {
    var rows0 = [].slice.call(tables[ti].rows);
    if (rows0.length < 3) continue;
    var map = null, hIdx = -1;
    for (var i = 0; i < rows0.length && i < 4 && !map; i++) {
      var cells0 = [].slice.call(rows0[i].cells), m = {}, hit = 0;
      for (var j = 0; j < cells0.length; j++) {
        var dd = dayOf(textOf(cells0[j]));
        if (dd) { m[j] = dd; hit++; }
      }
      if (hit >= 4) { map = m; hIdx = i; }
    }
    if (!map) continue;
    var score = rows0.length * 10 + Object.keys(map).length;
    if (tables[ti].offsetParent !== null) score += 1000;   /* 可见的表优先 */
    if (score > bestScore) { bestScore = score; best = tables[ti]; bestMap = map; bestHeader = hIdx; }
  }

  if (!best) {
    /* 没有"星期几"表头的网格表,可能是「列表」式课表 */
    var infoOnly = parseInfoTables();
    if (!infoOnly.length) infoOnly = parseListTables();
    if (infoOnly.length) {
      return JSON.stringify({ ok: true, msg: '', blocks: infoOnly, times: [] });
    }
    var bodyText = '';
    var extra = '';
    try {
      bodyText = (document.body && document.body.innerText || '').replace(/\s+/g, ' ').trim();
      var imgs = document.images, loaded = 0;
      for (var ii = 0; ii < imgs.length; ii++) {
        if (imgs[ii].complete && imgs[ii].naturalWidth > 0) loaded++;
      }
      var cs = document.body ? getComputedStyle(document.body) : null;
      extra = ' | 图' + loaded + '/' + imgs.length +
        ' | body底色' + (cs ? cs.backgroundColor : '?') +
        ' | 页高' + (document.body ? document.body.scrollHeight : 0) +
        ' 视口' + window.innerHeight +
        ' 宽' + window.innerWidth;
    } catch (e) { }
    return JSON.stringify({
      ok: false,
      msg: '没找到课表表格(标题:' + (document.title || '空') +
        ' / 正文' + bodyText.length + '字 / 表' + document.querySelectorAll('table').length + '个)' + extra +
        (bodyText ? ' 片段:' + bodyText.substring(0, 30) : ' —— 页面空白,可能没登录成功'),
      blocks: [],
      times: []
    });
  }

  var grid = expand(best);
  var hasDayCol0 = !!bestMap[0];
  var out = [], times = [], miss = [];
  var seen = [];
  var lastEnd = 0;

  for (var r = bestHeader + 1; r < grid.length; r++) {
    var rowCells = grid[r] || [];
    if (!rowCells.length) continue;
    var label = hasDayCol0 ? '' : textOf(rowCells[0]);
    var pr = periodRange(label);
    var rowPeriod = pr[0] || (r - bestHeader);
    var rowTime = timeRange(label);
    if (rowTime) {
      while (times.length < rowPeriod) times.push('');
      if (!times[rowPeriod - 1]) times[rowPeriod - 1] = rowTime;
    }

    for (var c = 0; c < rowCells.length; c++) {
      var cell = rowCells[c];
      var day = bestMap[c];
      if (!cell || !day) continue;
      if (seen.indexOf(cell) >= 0) continue;
      seen.push(cell);

      /* 这一格向下覆盖到第几行(合并单元格 = 连堂) */
      var rEnd = r;
      for (var rr = r + 1; rr < grid.length; rr++) {
        if (grid[rr] && grid[rr][c] === cell) rEnd = rr; else break;
      }
      var endLabel = hasDayCol0 ? null : periodRange(textOf((grid[rEnd] || [])[0]));
      var endPeriod = (endLabel && endLabel[1]) ? endLabel[1] : (rowPeriod + (rEnd - r));
      if (endPeriod < rowPeriod) endPeriod = rowPeriod;
      if (endPeriod > lastEnd) lastEnd = endPeriod;

      /* ① 只有当真的取到标注字段(<font title="周次/地点/教师">)时才走标注解析 */
      var labs = parseLabeledCourses(cell);
      var usedLabeled = false;
      for (var lc = 0; lc < labs.length; lc++) {
        var lab = labs[lc];
        if (!(lab.weeks || lab.location || lab.teacher)) continue;
        var pLab = periodRange(lab.periods);
        var spL = pLab[0] || rowPeriod;
        var epL = pLab[1] ? (pLab[1] >= spL ? pLab[1] : spL) : Math.max(spL, endPeriod);
        if (spL > 0 && spL <= 60) {
          out.push({
            day: day, start: spL, end: epL, name: lab.name,
            teacher: lab.teacher, weeks: lab.weeks, location: lab.location
          });
          usedLabeled = true;
        }
      }
      if (usedLabeled) continue;

      /* ② 否则按行归位:课程名(1-2节) / 周次 / 地点 / 教师 / 教学班 */
      var blocks = blocksOf(cell);
      for (var bi = 0; bi < blocks.length; bi++) {
        var a = analyzeLines(blocks[bi]);
        if (!a || !a.name) continue;
        var pA = periodRange(a.periods);
        var sp = pA[0] || rowPeriod;
        var ep = pA[1] ? (pA[1] >= sp ? pA[1] : sp) : Math.max(sp, endPeriod);
        /* 节次必须落在一天合理的范围内(最多十几节),不合理就用行号推出的节次 */
        if (sp < 1 || sp > 20) {
          sp = rowPeriod;
          ep = Math.max(rowPeriod, endPeriod);
        }
        if (!sp || sp > 30) continue;
        out.push({
          day: day, start: sp, end: (ep >= sp ? ep : sp), name: a.name,
          teacher: a.teacher, weeks: a.weeks, location: a.location
        });
      }
    }
  }
  /* 网格没解析出东西时,再试「课表信息」列表 和 「课程名称」列表 */
  /* 选中的网格表若是隐藏的(用户切到了"列表"视图),优先用列表式解析结果 */
  if (best && best.offsetParent === null) {
    var infoVis = parseInfoTables();
    if (!infoVis.length) infoVis = parseListTables();
    if (infoVis.length) {
      return JSON.stringify({ ok: true, msg: '', blocks: infoVis, times: [] });
    }
  }
  if (!out.length) {
    var infoOut = parseInfoTables();
    if (infoOut.length) out = infoOut;
  }
  if (!out.length) {
    var listOut = parseListTables();
    if (listOut.length) out = listOut;
  }
  /* 兜底:网格视图(表格)有时只能解析出残缺结果,
     此时把「课表信息」「课程名称」两条路也跑一遍,谁解析出的课多就用谁 ——
     用户不用关心当前停在哪个视图。 */
  if (best && best.offsetParent !== null) {
    var altInfo = parseInfoTables();
    var altList = parseListTables();
    var alt = altInfo.length >= altList.length ? altInfo : altList;
    if (alt.length > out.length) out = alt;
  }

  if (!out.length) {
    /* 还是空的话,把这张表的内容结构报出来,方便定位 */
    var probes = [];
    for (var rr2 = bestHeader + 1; rr2 < Math.min(grid.length, bestHeader + 4); rr2++) {
      var cs4 = grid[rr2] || [];
      var parts = [];
      for (var cc2 = 0; cc2 < cs4.length; cc2++) {
        var tx = norm(textOf(cs4[cc2]));
        var im = cs4[cc2].querySelectorAll('img').length;
        if (tx.length || im) parts.push(cc2 + ':' + tx.length + '字' + (im ? '/' + im + '图' : ''));
      }
      if (parts.length) probes.push('r' + rr2 + '(' + parts.join(' ') + ')');
    }
    /* 解析追踪:解析器在每个格子里到底看到了什么 */
    var trace = [];
    trace.push('best id=' + (best.id || '-') + ' visible=' + (best.offsetParent !== null) +
      ' headerRow=' + bestHeader + ' dayCols=' + JSON.stringify(bestMap));
    for (var tr = bestHeader + 1; tr < Math.min(grid.length, bestHeader + 3); tr++) {
      var rc = grid[tr] || [];
      for (var tc = 0; tc < rc.length; tc++) {
        if (!bestMap[tc] || !rc[tc]) continue;
        var cellT = rc[tc];
        var rawHtml = (cellT.innerHTML || '').replace(/\s+/g, ' ').substring(0, 220);
        var bl = blocksOf(cellT);
        var an = bl.length ? analyzeLines(bl[0]) : null;
        trace.push('r' + tr + 'c' + tc + ' 周' + bestMap[tc] + ' html=[' + rawHtml + ']');
        trace.push('   长度: innerHTML=' + (cellT.innerHTML || '').length +
          ' textContent=' + (cellT.textContent || '').length +
          ' innerText=' + (cellT.innerText || '').length);
        trace.push('   lines=' + JSON.stringify(bl[0] || null));
        trace.push('   analyzed=' + JSON.stringify(an));
        if (trace.length > 30) break;
      }
      if (trace.length > 30) break;
    }
    return JSON.stringify({
      ok: true,
      msg: '表格没解析出课程(共' + grid.length + '行,星期列[' + Object.keys(bestMap).join(',') + ']' +
        (probes.length ? ' 探针:' + probes.join(' ') : ' 数据行是空的') +
        (miss.length ? ' 缺星期或节次:' + miss.slice(0, 3).join(';') : '') +
        ');若当前是「表格」或「输出PDF」视图,请先切到「列表」视图再抓取',
      blocks: [],
      times: times,
      trace: trace.join('\n')
    });
  }

  return JSON.stringify({ ok: true, msg: '', blocks: out, times: times });
 } catch (err) {
  return JSON.stringify({
    ok: false,
    msg: '抓取脚本出错:' + (err && err.message ? err.message : String(err)),
    blocks: [],
    times: []
  });
 }
})()
"""

/**
 * 抓取失败时执行的页面结构转储脚本:把页面上每张表的行列结构、前几行内容写出来,
 * 便于定位"到底哪张表才是课表"。结果会存到 App 外部目录 scrape_debug.txt。
 */
internal const val DEBUG_JS = """
(function () {
  function norm(s) { return (s || '').replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim(); }
  function textOf(el) { return el ? (el.innerText || el.textContent || '') : ''; }
  function allTables() {
    var out = [].slice.call(document.querySelectorAll('table'));
    var fr = document.querySelectorAll('iframe,frame');
    for (var i = 0; i < fr.length; i++) {
      try { var d = fr[i].contentDocument; if (d) out = out.concat([].slice.call(d.querySelectorAll('table'))); } catch (e) { }
    }
    return out;
  }
  var L = [];
  try {
    L.push('URL: ' + location.href);
    L.push('TITLE: ' + document.title);
    var ts = allTables();
    L.push('TABLES: ' + ts.length);
    for (var i = 0; i < ts.length; i++) {
      var tb = ts[i];
      var rows = [].slice.call(tb.rows);
      var maxc = 0;
      for (var r = 0; r < rows.length; r++) maxc = Math.max(maxc, rows[r].cells.length);
      L.push('');
      L.push('--- table#' + i + ' id=' + (tb.id || '-') + ' class=' + (tb.className || '-') +
        ' rows=' + rows.length + ' maxcols=' + maxc + ' visible=' + (tb.offsetParent !== null));
      for (var r2 = 0; r2 < Math.min(rows.length, 5); r2++) {
        var cs = [].slice.call(rows[r2].cells), parts = [];
        for (var c = 0; c < cs.length; c++) {
          var t = norm(textOf(cs[c]));
          parts.push(c + ':[' + t.substring(0, 70) + ']' +
            (cs[c].rowSpan > 1 ? ' rs' + cs[c].rowSpan : '') +
            (cs[c].colSpan > 1 ? ' cs' + cs[c].colSpan : ''));
        }
        L.push('  r' + r2 + ': ' + parts.join(' | '));
      }
    }
  } catch (e) {
    L.push('DUMP ERROR: ' + (e && e.message ? e.message : e));
  }
  return L.join('\n');
})()
"""