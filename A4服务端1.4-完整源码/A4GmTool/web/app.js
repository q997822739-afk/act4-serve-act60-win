'use strict';
/* S1A5 网页 GM —— 无依赖前端。所有业务都走 /api/*，后端复用服务端自己的类。
   写模型两条路：服务端在跑时【发放】走邮箱（postal 追加一封不过期的系统邮件，服务端
   自己背包满时也这么寄）—— 在线就能发；角色在游戏中时服务端会在线把邮件推给他（不用小退），
   角色停在选择界面则进游戏后领取。改数值 / 清背包 / 改物品
   这类写的是服务端内存里拿着的数据，服务端在跑时仍被拒（先停服、写完、重启、重新选角）。
   服务端已停止时【发放】直写背包（写前自动备份），和以前一样。 */

const state = {
  status: null,
  characters: [],
  characterId: 0,
  page: 'overview',
  catalog: { query: '', kind: null, part: null, subtype: null, job: null, rarity: -1, min: '', max: '', page: 1, size: 60, total: 0, totalPages: 1, items: [] },
  kinds: [],
  selectedItem: null,
  inventory: { container: 0, rows: [], query: '', rarity: -1, selected: null },
  avatars: [],
  skills: { remainSp: 0, rows: [] },
  quests: [],
  loadout: null,
  mailbox: [],
  gmlist: { rows: [], accounts: [], selected: null },
  log: []
};

/* 职业 / 转职名（ACT5 六个基础职业，job 0..5；grow 低半字节 = 一转分支号，0=未转职） */
const JOB_NAMES = ['鬼剑士', '格斗家', '神枪手', '魔法师', '圣职者', '女枪手'];
/* 键 = 职业号（与 charac_info.job 一致：0=鬼剑士）；值 = 该职业每个一转分支的名字。 */
const GROW_NAMES = {
  0: ['未转职', '剑魂', '鬼泣', '狂战士', '阿修罗', '其他'],
  1: ['未转职', '气功师', '散打', '街霸', '柔道家', '其他'],
  2: ['未转职', '漫游枪手', '枪炮师', '机械师', '弹药专家', '其他'],
  3: ['未转职', '元素师', '召唤师', '战斗法师', '魔道学者', '其他'],
  4: ['未转职', '圣骑士', '蓝拳圣使', '驱魔师', '复仇者', '其他'],
  5: ['未转职', '漫游枪手', '枪炮师', '机械师', '弹药专家', '其他']
};

/* 游戏内命令簿（照抄 GM命令.txt 的有效命令；[无系统] 的不列） */
const GM_COMMANDS = [
  ['#ehdgusrlwn', '开启 GM 命令（再敲一次关闭）'],
  ['#realmode', '关闭 GM 模式'],
  ['#levelto 60', '直接跳到指定等级（1..60）'],
  ['#level up', '升一级'],
  ['#level down', '降一级'],
  ['#grow 1 3', '转职：1 固定 + 职业编号 0..5，按新分支重建技能、SP 重算'],
  ['#clear all skill', '技能初始化：清空全部技能，SP 按等级重算'],
  ['#add skill 100 5', '把技能设到指定等级'],
  ['#add item 3171 10', '获取物品（可堆叠截到堆叠上限；时装用 #add avatar）'],
  ['#add avatar 12345 1 30 0', '发时装：物品号 + 部位 + 期限(天，0/99=自带) + 颜色'],
  ['#money 10000000', '设金币（设成这个数；填 0 不动）'],
  ['#coin 1', '扣复活币（是扣不是加；0/负数不动）'],
  ['#enchant 0 5 12345', '写 enchant：容器号(0背包/1装扮/2金库/3穿戴/7宠物/12账号金库) + 槽号 + 值'],
  ['#addinfo 0 9 23207', '写附加信息：容器号 + 槽号 + 值'],
  ['#cinven', '清空整个背包'],
  ['#dirty avatar', '清空整个装扮栏'],
  ['#upgrade success', '下一次强化必成（fail=必败 / random=恢复随机）'],
  ['#mid 角色名', '查角色属于哪个账号'],
  ['#kick 角色名', '把在线角色踢下线（不能踢自己）'],
  ['#setgm add 10000', '把账号加进 gm_manifest（账号编号；重启生效）'],
  ['#setgm remove 10000', '从 gm_manifest 撤销 GM 资格'],
  ['#questdone', '进行中的任务立刻可交（去任务书点完成；免检只免这一次）'],
  ['#clearquest 100 101', '把任务直接标成已通关（重选角色才生效）'],
  ['#clearquest all', '够得着的任务全标完成'],
  ['#event list', '列出活动槽（●开 / ○关）'],
  ['#startevent <号> [p1] [p2]', '开启活动'],
  ['#endevent <号>', '结束活动'],
  ['#expertjob type 1', '转附魔师（ACT5 只有 1；0=卸职）'],
  ['#expertjob 1000', '设副职业熟练度（一次最多升一级，要升满连着敲）'],
  ['#learnrecipe all', '学完全部配方（本项目测试工具）']
];

/* ---------------- 基础工具 ---------------- */

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

function esc(value) {
  return String(value === null || value === undefined ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function fmt(value) {
  return (value === null || value === undefined || value === '') ? '—' : String(value);
}

function num(value) {
  return typeof value === 'number' ? value.toLocaleString('en-US') : fmt(value);
}

let csrfToken = '';

async function api(path, options) {
  const request = Object.assign({}, options);
  if (request.method === 'POST') {
    request.headers = { 'Content-Type': 'application/json', 'X-A4-GM-CSRF': csrfToken };
  }
  const response = await fetch(path, request);
  if (response.status === 401) window.location.replace('/login');
  let body = null;
  try { body = await response.json(); } catch (e) { body = null; }
  if (!response.ok) throw new Error((body && body.error) || `请求失败（HTTP ${response.status}）`);
  return body;
}

const post = (path, data) => api(path, { method: 'POST', body: JSON.stringify(data || {}) });

function toast(message, kind) {
  const host = $('#toastHost');
  const node = document.createElement('div');
  node.className = 'toast ' + (kind || '');
  node.textContent = message;
  host.appendChild(node);
  setTimeout(() => { node.style.opacity = '0'; node.style.transition = 'opacity .3s'; }, kind === 'error' ? 8000 : 4200);
  setTimeout(() => node.remove(), kind === 'error' ? 8600 : 4800);
}

function setStatus(text) { $('#statusText').textContent = text; }

function iconBox(itemId, big) {
  const cls = big ? 'icon-box lg' : 'icon-box';
  return `<div class="${cls}"><img src="/api/icon/?id=${itemId}&v=2" loading="lazy" decoding="async" alt=""
    onerror="this.style.display='none';this.parentNode.classList.add('empty-icon')"></div>`;
}

function rarityStyle(rarity) {
  const n = Math.max(0, Math.min(6, Number(rarity) || 0));
  return `color:var(--rarity-${n})`;
}

function jobName(job) {
  return JOB_NAMES[job] || ('职业' + job);
}

/* grow_type 位口径（照服务端 StatService）：低 4 位 = 一转分支(0..5)，高 4 位 = 觉醒段(0..2)。
   以前这里写成 `& 7` / `>> 3` —— 觉醒位会串进分支号、觉醒又读不出来。 */
function growText(growType, job) {
  const first = growType & 0x0F;
  const second = (growType >> 4) & 0x07;
  if (first === 0) return '未转职';
  const names = GROW_NAMES[job] || [];
  const base = names[first] || ('分支' + first);
  const stage = second === 0 ? '' : (second === 1 ? '（一次觉醒）' : (second === 2 ? '（二次觉醒）' : `（觉醒 ${second}）`));
  return base + stage;
}

/**
 * 按基础职业填「一转」「觉醒」两个下拉。换基础职业时重调一次，把列表换成新职业的
 * （每个职业的分支表不同：鬼剑士是剑魂/鬼泣…，格斗家是气功师/散打…）。
 * 觉醒下拉只按"有没有一转"启用（未转职时禁用并归零）。
 */
function fillBranchSelects(job, firstGrow, secondGrow) {
  const first = $('#growFirst');
  const second = $('#growSecond');
  if (!first) return;
  first.innerHTML = (GROW_NAMES[job] || ['未转职']).map((name, i) =>
    `<option value="${i}">${i} ${esc(name)}</option>`).join('');
  // 换职业时旧的 firstGrow 可能超出新职业的分支数（如女枪手只有 4 个）——夹回合法范围。
  const maxFirst = (GROW_NAMES[job] || ['未转职']).length - 1;
  const f = Math.max(0, Math.min(firstGrow || 0, maxFirst));
  first.value = String(f);
  if (second) {
    // 觉醒只到"一次觉醒"（ACT5 单机版的口径，服务端 secondGrow 只认 0/1）。
    second.disabled = f === 0;
    second.value = f === 0 ? '0' : String(Math.max(0, Math.min(secondGrow || 0, 1)));
  }
}

function table(headers, rows, options) {
  const opts = options || {};
  if (!rows.length) return `<div class="empty">${esc(opts.empty || '没有数据')}</div>`;
  const head = headers.map((h) =>
    `<th class="${h.num ? 'num' : ''}${h.cls ? ' ' + h.cls : ''}"${h.width ? ` style="width:${typeof h.width === 'number' ? h.width + 'px' : h.width}"` : ''}>${esc(h.label)}</th>`).join('');
  const body = rows.join('');
  return `<table${opts.fixed ? ' class="fixed"' : ''}><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`;
}

/* ---------------- 状态与顶栏 ---------------- */

async function refreshStatus(silent) {
  try {
    state.status = await api('/api/status');
  } catch (e) {
    if (!silent) toast('读取状态失败：' + e.message, 'error');
    return;
  }
  try {
    const logBody = await api('/api/log');
    state.log = logBody.log || [];
  } catch (e) { /* 日志拿不到不挡状态 */ }
  renderPills();
  renderStatusbar();
  renderAccountChip();
  if (state.page === 'overview') renderOverview();
  if (state.page === 'environment') { renderEnvForm(); renderEnvLog(); }
}

function renderPills() {
  const s = state.status;
  if (!s) return;
  const pill = (kind, label, value) => `<span class="pill ${kind}"><span class="dot"></span>${esc(label)} <b>${esc(value)}</b></span>`;
  const out = [];
  out.push(s.dbReady ? pill('ok', '数据库', s.dbName || '已就绪') : pill('bad', '数据库', '未打开'));
  out.push(s.serverUp ? pill('warn', '服务端', `运行中:${s.gamePort}`) : pill('ok', '服务端', '已停止'));
  out.push(s.serverUp ? pill('busy', '发放', '在线（进邮箱）') : pill('ok', '发放', '离线（进背包）'));
  // 写库：2026-10-10 起服务端自己记录"角色在不在游戏里"（server_presence 表），工具按角色自动判断 ——
  //   停服 = 随便写；在跑且目标角色不在游戏里 = 自动可写（小退后直接就能改，不用点任何开关）；
  //   在跑且目标角色在游戏中 = 拒写。老服务端（没有该表）才退回手动「已小退」开关。
  const auto = !!s.autoDetect;
  if (!s.serverUp) {
    out.push(pill('ok', '写库', '可写（服务端已停）'));
  } else if (auto) {
    const n = Number(s.inGameCount);
    out.push(n > 0 ? pill('bad', '写库', `拒写（${n} 个角色在游戏中）`)
                   : pill('ok', '写库', '自动可写（无人游戏中）'));
  } else if (s.writeAllowed) {
    out.push(pill('warn', '写库', '可写（已小退）'));
  } else {
    out.push(pill('bad', '写库', '拒写（先停服或小退）'));
  }
  out.push(s.pvfReady ? pill('ok', 'PVF', num(s.itemCount) + ' 件') : pill('bad', 'PVF', '未读取'));
  out.push(s.catalogReady ? pill('ok', '目录', num(s.catalogCount) + ' 项') : pill('busy', '目录', '构建中'));
  out.push(s.imagepacksReady ? pill('ok', '图标库', '就绪') : pill('bad', '图标库', '未找到'));
  $('#pills').innerHTML = out.join('');

  // 「小退」指示/开关：新服务端自动识别（只读指示，不用点）；老服务端才需要手动开关。
  const tg = $('#shutToggle');
  if (tg) {
    if (auto) {
      // 自动模式：按钮变成状态指示，点它没意义 -> 禁用
      tg.classList.add('active');
      tg.disabled = true;
      tg.textContent = '自动识别小退 ✓';
      tg.title = '这份服务端会自己记录角色在不在游戏里，工具按角色自动判断能不能写 —— '
        + '游戏里小退到【角色选择界面】后，直接再点一次要改的功能即可，不用点任何开关。';
    } else {
      const active = !!(s.serverUp && s.shutToSelect);
      tg.classList.toggle('active', active);
      tg.disabled = !s.serverUp;
      tg.textContent = active ? '已小退 ✓（可写库）' : '已小退';
      tg.title = !s.serverUp
        ? '服务端没在跑，不需要这个开关（现在写库直接可用）。'
        : (s.shutToSelect
          ? '已放行：服务端在跑时也能写库。角色一旦进游戏请点一下关掉它。'
          : '这份服务端不支持自动识别小退。在游戏里小退到【角色选择界面】后点这里放行。');
    }
  }
}

function renderStatusbar() {
  const s = state.status;
  if (!s) return;
  const scope = s.accountId > 0 ? `只看账号「${s.account || s.accountId}」` : '未限定账号（显示全部）';
  $('#statusRight').textContent = `端口 ${s.gmPort} · 物品 ${num(s.itemCount)} · 角色 ${num(s.characterCount)} · ${scope}`;
}

/** 顶栏的账号徽标：认出了就写明只看这个账号，认不出（库/配置里没有）就标出来免得误会。 */
function renderAccountChip() {
  const host = $('#accountChip');
  if (!host) return;
  const s = state.status;
  if (!s) { host.textContent = ''; host.className = 'account-chip'; return; }
  if (s.accountId > 0) {
    host.className = 'account-chip ok';
    host.textContent = `账号 ${s.account || s.accountId}`;
    host.title = `只显示与操作账号「${s.account}」（账号编号 ${s.accountId}）的角色；别的账号的角色被挡住。`;
  } else {
    host.className = 'account-chip warn';
    host.textContent = s.account ? `账号 ${s.account}（库里查不到）` : '账号未限定';
    host.title = (s.account
      ? `配置里写的是账号「${s.account}」，但这个库里没有它 —— `
      : '没读到登录账号（启动器 config / --account 都没给）—— ')
      + '现在会显示全部账号的角色。想限定就在启动器里配账号，或启动时加 --account <账号名>。';
  }
}

/* ---------------- 角色 ---------------- */

async function refreshCharacters(silent) {
  try {
    const body = await api('/api/characters');
    state.characters = body.characters || [];
  } catch (e) {
    if (!silent) toast('读取角色列表失败：' + e.message, 'error');
    return;
  }
  const select = $('#characterSelect');
  const keep = state.characterId;
  select.innerHTML = state.characters.map((c) =>
    `<option value="${c.characNo}">${esc(c.name)}（${esc(jobName(c.job))} Lv${c.lev}）</option>`).join('');
  if (keep && state.characters.some((c) => c.characNo === keep)) {
    select.value = String(keep);
  } else if (state.characters.length) {
    state.characterId = state.characters[0].characNo;
    select.value = String(state.characterId);
  } else {
    state.characterId = 0;
  }
  const del = $('#deleteCharSelect');
  if (del) {
    del.innerHTML = state.characters.map((c) =>
      `<option value="${c.characNo}">${esc(c.name)}（${esc(jobName(c.job))} Lv${c.lev}）</option>`).join('');
  }
  if (state.page === 'overview') renderOverview();
  if (state.page === 'character') refreshCharacterDetail().catch(() => {});
}

function currentCharacter() {
  return state.characters.find((c) => c.characNo === Number(state.characterId)) || null;
}

async function refreshCharacterDetail() {
  if (!state.characterId) return;
  try {
    state.characterDetail = await api('/api/character?characNo=' + state.characterId);
  } catch (e) {
    toast('读取角色失败：' + e.message, 'error');
    return;
  }
  if (state.page === 'character') renderCharacterPage();
  if (state.page === 'overview') renderOverview();
}

function renderOverview() {
  const s = state.status;
  const cards = [];
  const stat = (k, v, mono) => `<div class="stat"><span class="k">${esc(k)}</span><span class="v${mono ? ' mono' : ''}" title="${esc(v)}">${esc(v)}</span></div>`;
  if (s) {
    cards.push(stat('数据库', s.db || '未打开', true));
    cards.push(stat('服务端', s.serverUp ? `运行中（:${s.gamePort}）` : '已停止'));
    cards.push(stat('PVF', s.pvf || '未读取', true));
    cards.push(stat('物品目录', s.catalogReady ? `${num(s.catalogCount)} 项` : (s.catalogError ? '构建失败' : '构建中')));
    cards.push(stat('图标库', s.imagepacksReady ? '就绪' : '未找到'));
    cards.push(stat('GM 端口', String(s.gmPort), true));
    cards.push(stat('账号角色', num(s.characterCount) + ' 个', true));
    cards.push(stat('发放物品', s.serverUp ? '在线（投邮箱；游戏中直接推达）' : '离线（直写背包）'));
    if (!s.serverUp) {
      cards.push(stat('数值 / 背包编辑 / 转职', '可用（服务端已停止，改完启动再选角生效）'));
    } else if (s.autoDetect) {
      const n = Number(s.inGameCount);
      cards.push(stat('数值 / 背包编辑 / 转职', n > 0
        ? `拒写（${n} 个角色在游戏中 —— 游戏里小退到选择界面即自动放行，无需点开关）`
        : '可用（服务端自动识别：当前无人在游戏中）'));
    } else if (s.writeAllowed) {
      cards.push(stat('数值 / 背包编辑 / 转职', '可用（已小退到选择界面）'));
    } else {
      cards.push(stat('数值 / 背包编辑 / 转职', '已锁定（先停服，或游戏里小退后打开「我已小退」）'));
    }
  }
  $('#statusCards').innerHTML = cards.join('');

  const c = currentCharacter();
  const detail = state.characterDetail && Number(state.characterDetail.characNo) === Number(state.characterId)
    ? state.characterDetail : null;
  if (c || detail) {
    const d = detail || c;
    const kvItem = (k, v, mono, wide) => `<div class="kv-item${wide ? ' wide' : ''}"><span class="k">${esc(k)}</span><span class="v${mono ? ' mono' : ''}" title="${esc(v)}">${esc(v)}</span></div>`;
    $('#overviewCharacter').innerHTML = `<div class="kv">`
      + kvItem('名字', d.name)
      + kvItem('职业', `${jobName(d.job)}（职业编号 ${d.job}）`)
      + kvItem('等级', `${d.lev}`)
      + kvItem('转职', growText(d.growType, d.job))
      + kvItem('累计经验', num(d.exp), true)
      + kvItem('金币', num(d.money), true)
      + kvItem('点券', num(d.cera), true)
      + kvItem('决斗胜点', num(d.winPoint), true)
      + kvItem('疲劳', `${d.fatigue ?? 0}（上限 ${d.maxFatigue ?? 70}）`)
      + `</div>`;
  } else {
    $('#overviewCharacter').innerHTML = '<div class="empty">没有角色 —— 服务端建角色后这里会显示</div>';
  }

  renderLog($('#overviewLog'));
}

function renderLog(target) {
  const lines = state.log.map((line) =>
    `<div class="log-line ${esc(line.level)}"><span class="t">${esc(line.time)}</span><span class="m">${esc(line.message)}</span></div>`).join('');
  target.innerHTML = lines || '<div class="empty">暂无记录</div>';
  target.scrollTop = target.scrollHeight;
}

/* ---------------- 角色与货币页 ---------------- */

function renderCharacterPage() {
  const d = state.characterDetail;
  if (!d || Number(d.characNo) !== Number(state.characterId)) return;
  const MAX_LEVEL = 60;
  const field = (label, key, value, hint) =>
    `<label>${esc(label)}<input type="number" data-field="${key}" value="${esc(value ?? '')}">`
    + (hint ? `<span class="hint">${esc(hint)}</span>` : '') + `</label>`;
  // 经验条按「累计经验」算：本级内进度 = 累计值 − 本级门槛；满级或门槛异常时按 100% 收尾。
  let meter = '';
  if (d.nextLevelExp !== undefined) {
    const from = Number(d.currentLevelExp || 0);
    const to = Number(d.nextLevelExp);
    const section = Math.max(0, to - from);
    const inLevel = Math.max(0, Number(d.exp) - from);
    const over = section > 0 && inLevel > section;
    const percent = (d.lev >= MAX_LEVEL || section <= 0) ? 100 : Math.min(100, 100 * inLevel / section);
    meter = `<div class="exp-meter">
        <div class="exp-bar${over ? ' over' : ''}"><span style="width:${percent.toFixed(1)}%"></span></div>
        <div class="exp-facts">
          <span>本级升满需 ${num(section)}</span>
          <span>本级内进度 ${num(inLevel)}（${percent.toFixed(1)}%）</span>
          <span>升到 ${Math.min(MAX_LEVEL, d.lev + 1)} 级的累计门槛 ${num(to)}</span>
        </div>
      </div>`;
  }
  $('#characterForm').innerHTML =
    field(`等级（1..${MAX_LEVEL}）`, 'lev', d.lev)
    + field('累计经验', 'exp', d.exp, '升到该级的绝对累计值')
    + field('SP 余量', 'remainSp', d.remainSp)
    + field('金币', 'money', d.money)
    + field('点券', 'cera', d.cera)
    + field('决斗胜点', 'winPoint', d.winPoint)
    + meter;
  $('#characterHint').textContent = '经验列存的是「升到该级的累计经验」：设等级会自动按服务端经验表填（等级变化时累计经验与 SP 一起重算）。';

  const job = Number(d.job);
  const nowEl = $('#growNow');
  if (nowEl) nowEl.textContent = `职业编号 ${job}（${JOB_NAMES[job] || job}） · 转职值 ${d.growType}（${growText(d.growType, job)}）`;
  // 基础职业下拉：0..5（含女枪手）。
  const jobSel = $('#jobSelect');
  if (jobSel) {
    jobSel.innerHTML = JOB_NAMES.map((name, i) =>
      `<option value="${i}">${i} ${esc(name)}</option>`).join('');
    jobSel.value = String(job);
  }
  // 一转/觉醒下拉：内容按**当前选中的基础职业**填（换职业时会重填，见 onJobChange）。
  const packed = Number(d.growType);
  fillBranchSelects(job, packed & 0x0F, (packed >> 4) & 0x07);


  const kvItem = (k, v, mono) => `<div class="kv-item"><span class="k">${esc(k)}</span><span class="v${mono ? ' mono' : ''}">${esc(v)}</span></div>`;
  $('#characterDetails').innerHTML = `<div class="kv">`
    + kvItem('角色编号', d.characNo, true)
    + kvItem('账号编号', d.mId, true)
    + kvItem('村庄', d.village, true)
    + kvItem('性别', d.sex === 0 ? '女' : '男')
    + kvItem('复活币', num(d.coin), true)
    + kvItem('已用疲劳', d.usedFatigue, true)
    + kvItem('已用 SP', num(d.usedSp), true)
    + kvItem('副职业', d.expertJob ? `类型 ${d.expertJob}` : '无')
    + `</div>`;
}

/* ---------------- 发放物品页 ---------------- */

async function refreshKinds() {
  try {
    const body = await api('/api/catalog/kinds');
    state.kinds = body.kinds || [];
  } catch (e) {
    toast('读取分类失败：' + e.message, 'error');
    return;
  }
  renderKindRail();
  // 分类到位后顺带把「职业 / 类型·材质」下拉的可选值补上（refreshCatalog 可能先于本次响应跑完）。
  renderCatalogFilters();
}

function renderKindRail() {
  const rail = $('#kindChips');
  const c = state.catalog;
  const item = (entry) => {
    const attr = entry.kind !== undefined
      ? `data-kind="${esc(entry.kind)}"`
      : `data-part="${esc(entry.part)}"`;
    return `<button type="button" class="cat-item${entry.sub ? ' sub' : ''}${entry.active ? ' active' : ''}" `
      + `${attr} aria-pressed="${entry.active ? 'true' : 'false'}">`
      + `<span class="cat-label">${esc(entry.label)}</span><span class="n">${num(entry.count)}</span></button>`;
  };
  const allCount = state.status ? (state.status.catalogCount || 0) : 0;
  const out = [item({ kind: '', label: '全部', count: allCount, active: c.kind === null })];
  for (const kind of state.kinds) {
    const active = c.kind === kind.id;
    out.push(item({ kind: kind.id, label: kind.name, count: kind.count, active }));
    const parts = kind.parts || [];
    if (!active || parts.length < 2) continue;
    // 部位子层：只在选中分类下展开（分类 → 部位两级），再细的材质/武器类型在顶部下拉里。
    const partTotal = parts.reduce((sum, part) => sum + part.count, 0);
    out.push('<div class="cat-sub" role="group" aria-label="部位">');
    out.push(item({ part: '', label: '全部部位', count: partTotal, active: !c.part, sub: true }));
    for (const part of parts)
      out.push(item({ part: part.id, label: part.name, count: part.count, active: c.part === part.id, sub: true }));
    out.push('</div>');
  }
  rail.innerHTML = out.join('');
  // 展开子层后，最里层的选中项可能被挤到视口外 —— 只把左栏自身滚到能看见它。
  const actives = rail.querySelectorAll('.cat-item.active');
  const deepest = actives[actives.length - 1];
  if (deepest && rail.scrollHeight > rail.clientHeight) {
    const railBox = rail.getBoundingClientRect();
    const box = deepest.getBoundingClientRect();
    if (box.bottom > railBox.bottom) rail.scrollTop += box.bottom - railBox.bottom + 8;
    else if (box.top < railBox.top) rail.scrollTop -= railBox.top - box.top + 8;
  }
}

/**
 * 顶部工具栏的两个二级下拉（照 60US 的 itemJobField / itemSubtypeField）：
 *   职业 —— 当前分类的 jobs（未选分类时把各分类的 jobs 合并）；分类没有职业这一维就藏起来。
 *   类型/材质 —— **按当前部位取** subtypes，标签用该部位的轴名（材质 / 武器类型 / 类型）；不足两项藏起来。
 * 分类 / 部位一变就要重置并重填（见 click 处理与 clear-catalog-filters）。
 */
function renderCatalogFilters() {
  const c = state.catalog;
  const kind = state.kinds.find((entry) => entry.id === c.kind) || null;
  let jobs = [];
  if (kind) {
    jobs = kind.jobs || [];
  } else {
    const merged = new Map();
    for (const entry of state.kinds)
      for (const job of (entry.jobs || [])) {
        const prev = merged.get(job.id);
        if (prev) prev.count += job.count;
        else merged.set(job.id, { id: job.id, name: job.name, count: job.count });
      }
    jobs = Array.from(merged.values());
  }
  const jobField = $('#itemJobField');
  jobField.hidden = jobs.length < 2;
  $('#itemJob').innerHTML = '<option value="">全部职业</option>' + jobs.map((job) =>
    `<option value="${esc(job.id)}" ${job.id === c.job ? 'selected' : ''}>${esc(job.name)} (${num(job.count)})</option>`).join('');
  if (jobs.length < 2 && c.job) c.job = null;

  // 类型 / 材质：按当前部位取 subtypes；没选部位时退回该分类下第一个带子类的部位
  // （武器分类只有一个部位，左栏不展开单部位子层，靠这里兜底才选得到「武器类型」）。
  const parts = kind ? (kind.parts || []) : [];
  const part = c.part
    ? parts.find((entry) => entry.id === c.part)
    : (parts.find((entry) => (entry.subtypes || []).length >= 2) || parts[0]);
  const subs = part ? (part.subtypes || []) : [];
  const axis = part ? (part.axis || '类型') : '类型';
  const subField = $('#itemSubtypeField');
  subField.hidden = subs.length < 2;
  $('#itemSubtypeLabel').textContent = axis;
  $('#itemSubtype').innerHTML = `<option value="">全部${esc(axis)}</option>` + subs.map((sub) =>
    `<option value="${esc(sub.id)}" ${sub.id === c.subtype ? 'selected' : ''}>${esc(sub.name)} (${num(sub.count)})</option>`).join('');
  if (subs.length < 2 && c.subtype) c.subtype = null;
}

async function refreshCatalog() {
  const c = state.catalog;
  const params = new URLSearchParams();
  if (c.query) params.set('query', c.query);
  if (c.kind) params.set('kind', c.kind);
  if (c.part) params.set('part', c.part);
  if (c.subtype) params.set('subtype', c.subtype);
  if (c.job) params.set('job', c.job);
  if (c.rarity >= 0) params.set('rarity', c.rarity);
  if (c.min !== '') params.set('min', c.min);
  if (c.max !== '') params.set('max', c.max);
  params.set('page', c.page);
  params.set('size', c.size);
  try {
    const body = await api('/api/catalog?' + params.toString());
    if (body.error) {
      $('#catalogTable').innerHTML = `<div class="empty">${esc(body.error)}</div>`;
      return;
    }
    c.total = body.total;
    c.totalPages = body.totalPages;
    c.items = body.items || [];
  } catch (e) {
    toast('读取目录失败：' + e.message, 'error');
    return;
  }
  renderCatalog();
}

function renderCatalog() {
  const c = state.catalog;
  hideHoverCard();
  const rows = c.items.map((item) =>
    `<tr class="clickable${state.selectedItem && state.selectedItem.id === item.id ? ' selected' : ''}" data-item="${item.id}">
      <td class="id">${item.id}</td>
      <td><div class="icon-cell">${iconBox(item.id)}<span class="name-cell" style="${rarityStyle(item.rarity)}">${esc(item.name || '（无名）')}</span></div></td>
      <td class="muted">${esc(typeText(item))}</td>
      <td style="${rarityStyle(item.rarity)}">${esc(rarityName(item.rarity))}</td>
      <td class="num">${item.minLevel || 0}</td>
      <td class="num">${stackText(item)}</td>
    </tr>`);
  $('#catalogTable').innerHTML = table(
    [{ label: 'ID', cls: 'id', width: 84 }, { label: '名称' }, { label: '类型', width: 112 },
     { label: '品质', width: 72 }, { label: '等级', num: true, width: 56 }, { label: '堆叠上限', num: true, width: 84 }],
    rows, { fixed: true, empty: '没有匹配的物品' });
  $('#catalogMeta').textContent = `共 ${num(c.total)} 件 · 第 ${c.page}/${c.totalPages} 页`
    + (state.status ? `（PVF ${num(state.status.itemCount)} 件）` : '');

  const pager = [];
  pager.push(`<button data-page="1" ${c.page <= 1 ? 'disabled' : ''}>« 首页</button>`);
  pager.push(`<button data-page="${c.page - 1}" ${c.page <= 1 ? 'disabled' : ''}>‹ 上一页</button>`);
  pager.push(`<span>第 ${c.page} / ${c.totalPages} 页</span>`);
  pager.push(`<button data-page="${c.page + 1}" ${c.page >= c.totalPages ? 'disabled' : ''}>下一页 ›</button>`);
  pager.push(`<button data-page="${c.totalPages}" ${c.page >= c.totalPages ? 'disabled' : ''}>末页 »</button>`);
  $('#catalogPager').innerHTML = pager.join('');

  renderCatalogFilters();
  renderGivePanel();
}

function rarityName(rarity) {
  return ['普通', '高级', '稀有', '神器', '史诗', '勇者'][rarity] || ('品级' + rarity);
}

function typeText(item) {
  if (item.kindName === '武器') return item.subtypeName ? `武器·${item.subtypeName}` : '武器';
  if (item.kindName === '防具') return item.subtypeName ? `${item.partName}·${item.subtypeName}` : item.partName;
  if (item.kindName === '时装') return item.jobName === '女枪手' ? `${item.partName}·女枪手` : (item.partName || '时装');
  return item.kindName;
}

/**
 * 堆叠上限那一列的文案。
 * 材料这类没有 [stack limit]（stackLimit=0）但是"按份发"的 —— 写「无上限」而不是「—」，
 * 免得看起来像不能堆叠（那正是老版把数量框一起禁掉的同一个误解）。
 */
function stackText(item) {
  if (!item.stackableItem) return '—';
  return item.stackLimit > 0 ? num(item.stackLimit) : '无上限';
}

/**
 * 物品详情段落（照 60US 的 itemDetailSections，悬停卡与发放设置面板共用）：
 * 属性行 → 特效绿字 → 介绍 → 详情 → 风味文本（两侧加中文引号）。
 * 空字段不渲染对应段落（不出现空段或"无"）。
 */
function itemDetailSections(item) {
  const out = [];
  // 这件属于哪几套（后端从 PVF 的 [set item] 反查）
  if (item.sets && item.sets.length)
    out.push(`<div class="tt-section"><div class="tt-recipe-title">套装</div>${
      item.sets.map((s) => `<div class="tt-effect">${esc(s)}</div>`).join('')}</div>`);
  if (item.stats && item.stats.length)
    out.push(`<div class="tt-section">${item.stats.map((line) => `<div class="tt-stat">${esc(line)}</div>`).join('')}</div>`);
  if (item.effects && item.effects.length)
    out.push(`<div class="tt-section">${item.effects.map((line) => `<div class="tt-effect">${esc(line)}</div>`).join('')}</div>`);
  // 时装词条（[avatar select ability] 各行，下标即发放时选的词条编号）
  if (item.avatarOptions && item.avatarOptions.length)
    out.push(`<div class="tt-section"><div class="tt-recipe-title">可选属性词条</div>${
      item.avatarOptions.map((line, index) => `<div class="tt-stat">${index} ${esc(line)}</div>`).join('')}</div>`);
  if (item.explain)
    out.push(`<div class="tt-section"><div class="tt-desc">${esc(item.explain)}</div></div>`);
  if (item.detailExplain)
    out.push(`<div class="tt-section"><div class="tt-desc detail">${esc(item.detailExplain)}</div></div>`);
  if (item.flavorText)
    out.push(`<div class="tt-section"><div class="tt-flavor">“${esc(item.flavorText)}”</div></div>`);
  return out.join('');
}

/** 末尾等宽小字：物品脚本路径（后端只给 path，没有单独的图标帧号）。 */
function itemTechLine(item) {
  // 图标源（后端 iconPath/iconFrame）+ 脚本路径：与 60US 那行等宽小字一致
  const icon = item.iconPath ? `${esc(item.iconPath)} #${item.iconFrame || 0}` : '无图标';
  return `<div class="hover-meta tt-tech">${icon}<br>PVF ${esc(item.path || '—')}</div>`;
}

function renderGivePanel() {
  const item = state.selectedItem;
  if (!item) {
    $('#giveMeta').textContent = '';
    $('#givePanel').innerHTML = '<div class="empty">左边挑一件物品</div>';
    return;
  }
  $('#giveMeta').textContent = `#${item.id}`;
  const isAvatar = item.kind === 'avatar';
  $('#givePanel').innerHTML = `
    <div class="give-head">
      ${iconBox(item.id, true)}
      <div class="give-head-text">
        <div class="give-title" style="${rarityStyle(item.rarity)}">${esc(item.name || '（无名）')}</div>
        <div class="give-sub">#${item.id} · ${esc(typeText(item))} · ${esc(rarityName(item.rarity))} · Lv${item.minLevel || 0}</div>
      </div>
    </div>
    <div class="give-scroll">
      ${itemDetailSections(item)}
      <div class="tt-section">
        <div class="tt-recipe-title">物品信息</div>
        <div class="tt-stat">堆叠上限：${item.stackableItem ? (item.stackLimit > 0 ? num(item.stackLimit) : '无上限（按份发，可一次发多个）') : '不可堆叠'}</div>
        <div class="tt-stat">参考价：${num(item.price || 0)}</div>
      </div>
      ${itemTechLine(item)}
    </div>
    <div class="give-fields">
      ${isAvatar ? `
        <label>部位<select data-give="avatarSlot"><option value="0">0</option><option value="1">1</option><option value="2">2</option>
          <option value="3">3</option><option value="4">4</option><option value="5">5</option><option value="6">6</option>
          <option value="7">7</option><option value="8">8</option></select></label>
        <label>期限<select data-give="avatarDays" ${avatarPeriodSelect(item)}</select></label>
        <label>属性词条<select data-give="avatarAbility" ${avatarOptionSelect(item)}></select></label>
      ` : `
        <label>数量<input type="number" data-give="count" value="${item.stackableItem ? giveCountDefault(item) : 1}" min="1" max="${giveCountMax(item)}" ${item.stackableItem ? '' : 'disabled title="装备一次一件"'}></label>
        <label>强化 +<input type="number" data-give="enhance" value="0" min="0" max="31" title="0 = 不强化；在线（邮箱）与离线发放都生效"></label>
        <label>耐久<input type="number" data-give="endurance" value="0" min="0" title="0 = 用 PVF 自带耐久"></label>
      `}
    </div>
    <div class="row-actions">
      <button class="btn btn-primary" data-action="give-item">${onlineGive() ? '在线发放到邮箱' : '离线发放到背包'}</button>
    </div>
    <div class="notice compact">${esc(giveNotice(isAvatar))}</div>`;
}

/** 发放面板底部那句说明：跟着在线/离线和物品类型走。 */
function giveNotice(isAvatar) {
  if (onlineGive()) {
    return isAvatar
      ? '时装直接写进装扮栏（在线可发；重新选角后可见）。属性词条就是脚本里可选的几条，发放后还能在「时装」页改。能不能用要看游戏里那一格空着。'
      : '服务端在跑 —— 发到邮箱（不过期的系统邮件）。角色在游戏中时，服务端几秒内把邮件直接推到邮箱并弹提醒，不用小退；'
        + '角色停在选择界面时，进游戏后即可领取。不写背包、不碰服务端内存。';
  }
  return isAvatar
    ? '时装发到装扮栏（离线写库：需要服务端已停止，写前自动备份）。属性词条发放后可在「时装」页改。'
    : '普通物品发到背包（离线写库：需要服务端已停止，写前自动备份）。';
}

/** 数量框的默认值：一律 1（2026-10-11 用户要求，以前默认顶格/100）。 */
function giveCountDefault(item) {
  return 1;
}

/** 时装期限下拉的选项：脚本 [avatar type select] 各档（0 = 永久）；没有档位的只能永久。 */
function avatarPeriodSelect(item) {
  const periods = Array.isArray(item.avatarPeriods) ? item.avatarPeriods : [];
  if (!periods.length) {
    return 'disabled><option value="0">永久</option>';
  }
  return '>' + periods.map((days) =>
    `<option value="${days}">${days > 0 ? days + ' 天' : '永久'}</option>`).join('');
}

/** 时装词条下拉的选项：脚本 [avatar select ability] 各行（后端已翻译成中文），下标 = 词条编号。 */
function avatarOptionSelect(item) {
  const options = Array.isArray(item.avatarOptions) ? item.avatarOptions : [];
  if (!options.length) {
    return 'disabled><option value="0">（这件没有可选词条）</option>';
  }
  return '>' + options.map((name, index) =>
    `<option value="${index}">${index} ${esc(name)}</option>`).join('');
}

/** 数量框的 max：没有 [stack limit] 的（材料）不设上限 —— 服务端按份收，能收多少收多少。 */
function giveCountMax(item) {
  const limit = Number(item.stackLimit) || 0;
  return limit > 0 ? limit : 9999;
}

/* ---------------- 物品悬停预览（.hover-card） ---------------- */

let hoverTimer = null;

function hideHoverCard() {
  $('#hoverCard').classList.remove('show');
  clearTimeout(hoverTimer);
}

/** 把物品卡片塞进浮窗并按锚点行摆位（照 60US：贴边上下翻转）。 */
function showHoverCard(item, anchor) {
  const card = $('#hoverCard');
  card.innerHTML = `
    <div class="hover-head">
      ${iconBox(item.id, true)}
      <div>
        <div class="hover-name" style="${rarityStyle(item.rarity)}">${esc(item.name || '（无名）')}</div>
        <div class="hover-meta">#${item.id} · ${esc(typeText(item))}</div>
        <div class="hover-meta">${esc(rarityName(item.rarity))} · Lv${item.minLevel || 0}${item.stackableItem ? ' · 堆叠上限 ' + (item.stackLimit > 0 ? num(item.stackLimit) : '无上限') : ' · 不可堆叠'}</div>
      </div>
    </div>
    ${itemDetailSections(item)}
    ${itemTechLine(item)}`;
  const rect = anchor.getBoundingClientRect();
  card.classList.add('show');
  const width = card.offsetWidth;
  const height = card.offsetHeight;
  const left = Math.min(window.innerWidth - width - 12, Math.max(8, rect.left + 40));
  const top = rect.bottom + 6 + height > window.innerHeight
    ? Math.max(8, rect.top - height - 6)
    : rect.bottom + 6;
  card.style.left = left + 'px';
  card.style.top = top + 'px';
}

/** 只在**名称列**上出卡（鼠标在行其它列或滚动时不弹），带 200ms 延迟。 */
function onCatalogOver(event) {
  const row = event.target.closest('tr[data-item]');
  const cell = event.target.closest('td');
  if (!row || !cell || !cell.querySelector('.name-cell')) {
    hideHoverCard();
    return;
  }
  clearTimeout(hoverTimer);
  hoverTimer = setTimeout(() => {
    const item = state.catalog.items.find((it) => it.id === Number(row.dataset.item));
    if (!item || !row.isConnected) return;
    showHoverCard(item, row);
  }, 200);
}

function onCatalogOut(event) {
  const row = event.target.closest('tr[data-item]');
  if (!row) return;
  // 只有真正离开这一行才收起（行内子元素之间移动不算离开）。
  if (event.relatedTarget && row.contains(event.relatedTarget)) return;
  hideHoverCard();
}

/* ---------------- 背包页 ---------------- */

const CONTAINERS = [
  [0, '背包'],
  [3, '穿戴'],
  [2, '金库'],
  [7, '宠物']
];

/** 「邮箱」这个伪容器（不是服务端容器）：选它时这一页显示邮件列表、可强行删除。 */
const MAILBOX_CONTAINER = 99;

async function refreshInventory() {
  if (!state.characterId) return;
  if (state.inventory.container === MAILBOX_CONTAINER) {
    await refreshMailbox();
    return;
  }
  try {
    const body = await api(`/api/inventory?characNo=${state.characterId}&container=${state.inventory.container}`);
    if (body.error) {
      toast(body.error, 'error');
      return;
    }
    state.inventory.rows = body.rows || [];
  } catch (e) {
    toast('读取背包失败：' + e.message, 'error');
    return;
  }
  renderInventory();
}

/** 容器切换条：四个真实容器 + 末尾一个「邮箱」（伪容器）。谁重画这一整块都由这里出。 */
function renderContainerChips() {
  const inv = state.inventory;
  const chips = CONTAINERS.map(([id, label]) =>
    `<button class="chip${inv.container === id ? ' active' : ''}" data-container="${id}">${esc(label)}</button>`);
  chips.push(`<button class="chip${inv.container === MAILBOX_CONTAINER ? ' active' : ''}" data-container="${MAILBOX_CONTAINER}">邮箱</button>`);
  $('#containerChips').innerHTML = chips.join('');
}

function renderInventory() {
  const inv = state.inventory;
  renderContainerChips();

  if (inv.container === MAILBOX_CONTAINER) {
    renderMailbox();
    return;
  }
  // 真实容器：显示检索/品质工具条
  $('#invToolbar').style.display = '';
  $('#inventoryNote').style.display = 'none';
  $('#inventoryMeta').textContent = state.characterId
    ? `${currentCharacter() ? currentCharacter().name : ''} 的容器 ${inv.container}`
    : '没选角色';
  const query = inv.query.trim().toLowerCase();
  const rows = inv.rows
    .filter((row) => inv.rarity < 0 || row.rarity === inv.rarity)
    .filter((row) => !query || (row.slot + ' ' + row.itemId + ' ' + (row.name || '')).toLowerCase().includes(query))
    .map((row) =>
      `<tr class="clickable${inv.selected === row.slot ? ' selected' : ''}" data-invslot="${row.slot}">
        <td class="num">${row.slot}</td>
        <td class="id">${row.itemId}</td>
        <td><div class="icon-cell">${iconBox(row.itemId)}<span class="name-cell" style="${rarityStyle(row.rarity)}">${esc(row.name || '目录外 ' + row.itemId)}</span></div></td>
        <td class="num">${row.stackableItem ? num(row.count) : '—'}</td>
        <td class="num">${row.upgrade ? '+' + row.upgrade : '—'}</td>
        <td class="num">${row.endurance || '—'}</td>
        <td class="num">${row.enchant || '—'}</td>
      </tr>`);
  $('#inventoryTable').innerHTML = table(
    [{ label: '槽', num: true, width: 60 }, { label: 'ID', num: true }, { label: '名称' },
     { label: '数量', num: true }, { label: '强化', num: true }, { label: '耐久', num: true }, { label: '附魔', num: true }],
    rows, { fixed: true, empty: '这个容器是空的' });
  renderInventoryActions();
}

function renderInventoryActions() {
  const slot = state.inventory.selected;
  const host = $('#inventoryActions');
  if (slot === null || slot === undefined) {
    host.innerHTML = '<span class="muted">点一行选中，可改强化 / 耐久 / 附魔 / 附加信息，或删除。</span>';
    return;
  }
  const row = state.inventory.rows.find((r) => r.slot === slot);
  host.innerHTML = `
    <span class="tag info">槽 ${slot}${row ? ' · ' + esc(row.name || row.itemId) : ''}</span>
    <label class="inline-field">强化 +<input type="number" data-edit="enhance" value="${row ? row.upgrade : 0}" min="0" max="31"></label>
    <label class="inline-field">耐久<input type="number" data-edit="endurance" value="${row ? row.endurance : 0}" min="0"></label>
    <button class="btn" data-action="inv-save-eq">保存强化 / 耐久</button>
    <label class="inline-field">附魔值<input type="number" data-edit="enchant" value="${row ? row.enchant : 0}" min="0"></label>
    <button class="btn" data-action="inv-save-enchant">保存附魔</button>
    <label class="inline-field">附加信息<input type="number" data-edit="addinfo" value="${row ? row.addInfo : 0}" min="0"></label>
    <button class="btn" data-action="inv-save-addinfo">保存附加信息</button>
    <button class="btn btn-danger" data-action="inv-delete">删除这件</button>`;
}

/* ---------------- 时装页 ---------------- */

async function refreshAvatars() {
  if (!state.characterId) return;
  try {
    const body = await api('/api/avatars?characNo=' + state.characterId);
    if (body.error) {
      toast(body.error, 'error');
      return;
    }
    state.avatars = body.rows || [];
  } catch (e) {
    toast('读取时装失败：' + e.message, 'error');
    return;
  }
  renderAvatars();
}

function renderAvatars() {
  $('#avatarMeta').textContent = state.characterId ? `${state.avatars.length} 件（装扮栏槽位 0..104）` : '没选角色';
  const rows = state.avatars.map((row) =>
    `<tr>
      <td class="num">${row.slot}</td>
      <td class="id">${row.itemId}</td>
      <td><div class="icon-cell">${iconBox(row.itemId)}<span class="name-cell" style="${rarityStyle(row.rarity)}">${esc(row.name || '目录外 ' + row.itemId)}</span></div></td>
      <td>${esc(row.partName || '—')}</td>
      <td>${esc(row.jobName || '—')}</td>
      <td class="muted">${esc(row.expire || '永久')}</td>
      <td>${avatarOptionCell(row)}</td>
      <td><button class="btn btn-tiny btn-danger" data-avatar-delete="${row.uiId}">删除</button></td>
    </tr>`);
  $('#avatarTable').innerHTML = table(
    [{ label: '槽', num: true, width: 60 }, { label: 'ID', num: true }, { label: '名称' },
     { label: '部位' }, { label: '职业' }, { label: '期限' }, { label: '属性词条', width: 240 },
     { label: '', width: 60 }],
    rows, { fixed: true, empty: '装扮栏是空的 —— 去「发放物品」页挑时装发放' });
}

/**
 * 时装词条单元格：脚本里有可选词条 → 下拉 + 「保存」（照 60US UpdateAvatarOption）；
 * 没有可选词条（老时装）→ 只显示当前编号。当前编号越界时提示重新选。
 */
function avatarOptionCell(row) {
  const options = Array.isArray(row.avatarOptions) ? row.avatarOptions : [];
  if (!options.length) {
    return `<span class="muted">#${row.abilityNo}（无可选词条）</span>`;
  }
  const bad = row.abilityNo >= options.length;
  const opts = (bad ? `<option value="" selected disabled>当前词条无效，请重新选择</option>` : '')
    + options.map((name, index) =>
      `<option value="${index}" ${index === row.abilityNo ? 'selected' : ''}>${index} ${esc(name)}</option>`).join('');
  return `<div class="row-actions" style="gap:4px">
      <select data-avatar-option="${row.uiId}" style="flex:1;min-width:150px">${opts}</select>
      <button class="btn btn-tiny btn-primary" data-avatar-save="${row.uiId}">保存</button>
    </div>`;
}

/* ---------------- 技能页 ---------------- */

async function refreshSkills() {
  if (!state.characterId) return;
  try {
    const body = await api('/api/skills?characNo=' + state.characterId);
    if (body.error) {
      toast(body.error, 'error');
      return;
    }
    state.skills = body;
  } catch (e) {
    toast('读取技能失败：' + e.message, 'error');
    return;
  }
  renderSkills();
}

function renderSkills() {
  $('#spInput').value = state.skills.remainSp ?? 0;
  const rows = (state.skills.rows || []).map((row) =>
    `<tr>
      <td class="num">${row.slot}</td>
      <td><div class="icon-cell">${skillIconBox(row.skillIndex)}<span class="name-cell">${esc(row.name || '（技能 ' + row.skillIndex + '）')}</span></div></td>
      <td><input type="number" class="skill-level" data-slot="${row.slot}" data-index="${row.skillIndex}" value="${row.level}" min="0" max="255" style="width:80px"></td>
      <td><button class="btn btn-tiny" data-skill-save="${row.slot}">保存</button></td>
    </tr>`);
  $('#skillTable').innerHTML = table(
    [{ label: '槽', num: true, width: 70 }, { label: '技能' }, { label: '等级', width: 110 }, { label: '', width: 80 }],
    rows, { fixed: true, empty: '没有已学的技能' });
}

/** 技能图标（技能号是按职业的，所以带上当前角色号让后端自己定职业）。取不到就不画。 */
function skillIconBox(skillIndex) {
  const ch = state.characterId || 0;
  return `<div class="icon-box"><img src="/api/skill-icon?characNo=${ch}&skillIndex=${skillIndex}" loading="lazy" decoding="async" alt=""
    onerror="this.style.display='none';this.parentNode.classList.add('empty-icon')"></div>`;
}

/* ---------------- 任务页 ---------------- */

async function refreshQuests() {
  if (!state.characterId) return;
  try {
    const body = await api('/api/quests?characNo=' + state.characterId);
    if (body.error) {
      toast(body.error, 'error');
      return;
    }
    state.quests = body;
  } catch (e) {
    toast('读取任务失败：' + e.message, 'error');
    return;
  }
  renderQuests();
}

function renderQuests() {
  const rows = (state.quests.running || []).map((q) => {
    const mats = (q.materials || []).map((m) => {
      const tag = m.gap > 0
        ? `<span class="tag warn">缺 ${num(m.gap)}</span>`
        : `<span class="tag ok">已够</span>`;
      return `<div class="material-line">${esc(m.name || ('#' + m.itemId))} ×${num(m.need)}<span class="muted">（已有 ${num(m.have)}）</span>${tag}</div>`;
    }).join('') || '<span class="muted">—</span>';
    return `<tr class="clickable" data-quest="${q.questId}">
      <td class="id">${q.questId}</td>
      <td>${esc(q.name || '（无名）')}</td>
      <td class="num">${q.trigger === 0 ? '<span class="tag ok">可交付</span>' : num(q.trigger)}</td>
      <td>${mats}</td>
    </tr>`;
  });
  $('#questTable').innerHTML = table(
    [{ label: '任务号', cls: 'id' }, { label: '名称', width: 200 }, { label: '进度', num: true, width: 90 }, { label: '材料（需求 / 已有 / 缺口）' }],
    rows, { fixed: true, empty: '没有进行中的任务' });
  $('#questMeta').textContent = `已完成 ${state.quests.clearedCount ?? 0} 个 · `;
}

/* ---------------- 一键紫装 ---------------- */

async function loadoutPreview() {
  if (!state.characterId) { toast('先选角色', 'error'); return; }
  try {
    const d = await api('/api/loadout?characNo=' + state.characterId);
    if (d.error) { toast(d.error, 'error'); return; }
    state.loadout = d;
    renderLoadout();
  } catch (e) {
    toast('挑装失败：' + e.message, 'error');
  }
}

function renderLoadout() {
  const d = state.loadout;
  if (!d) return;
  const cells = (d.picks || []).map((p) => {
    if (p.found) {
      // 套装成员标所属套名；不查漏补缺了，所以不再标「已有·跳过」
      const badge = p.inSet ? '<span class="tag info">套装</span>' : '';
      return `<div class="set-member" title="${esc(p.partName)} ${esc(p.name)}（#${p.id}，Lv${p.minLevel}）">
        ${iconBox(p.id)}
        <span class="set-member-name" style="${rarityStyle(p.rarity)}">${esc(p.partName)}·${esc(p.name)}</span>
        ${badge}
        <span class="muted" style="margin-left:auto;font-family:var(--mono);font-size:11px">Lv${p.minLevel}</span>
      </div>`;
    }
    return `<div class="set-member bad" title="${esc(p.partName)}该等级段没有可发的">
      <span class="set-member-name muted">${esc(p.partName)}·该等级段没有可发的</span>
    </div>`;
  }).join('');
  const total = d.foundCount || 0;
  $('#loadoutPanel').innerHTML = `
    <div class="give-head">
      <div class="give-head-text">
        <div class="give-title">${esc(d.title || '')}</div>
        <div class="give-sub">${d.setName
          ? `整套「${esc(d.setName)}」五件 + 武器/首饰逐件挑`
          : '九个部位逐件挑（该等级段没有成套的防具）'}</div>      </div>
    </div>
    <div class="tt-section">
      <div class="set-members">${cells}</div>
    </div>
    <div class="row-actions" style="margin-top:8px">
      ${total > 0
        ? `<button class="btn btn-primary" data-action="loadout-apply">${onlineGive() ? '在线发放整套（进邮箱）' : '离线发放整套'}（${total} 件）</button>`
        : '<span class="tag bad">九个部位一件都没挑到（等级或目录问题）</span>'}
      <span class="muted" style="align-self:center">共 ${total} 件</span>
    </div>
    <div class="notice compact" style="margin-top:6px">套装成员直接来自 PVF 的 <code>[set item]</code> 段（物品脚本自己写的成员号，不是按名字猜）；
      防具按转职材质成套、武器/首饰逐件挑最高档。<b>整套照发，不检查背包里有没有</b> —— 想再要一套（换装 / 送人 / 之前那件废了）直接点，重复与否你自己定。
      ${onlineGive()
        ? '在线发放：走邮箱，不碰服务端内存、不备份。'
        : '离线写库：服务端要已停止，写前自动备份。'}</div>`;
}

async function loadoutApply() {
  const d = state.loadout;
  if (!d) return;
  const online = onlineGive();
  const total = d.foundCount || 0;
  const setPart = d.setName ? `「${d.setName}」` : '';
  if (!confirm(online
    ? `在线发放${setPart}整套 ${total} 件到邮箱？
（不检查背包 —— 如果角色已经有了，会再发一套重复的）

服务端在跑 —— 这几件会各投一封不过期的系统邮件。角色在游戏中时服务端几秒内直接推给他（不用小退），否则进游戏后即可领取。`
    : `离线发放${setPart}整套 ${total} 件到背包？
（不检查背包 —— 如果角色已经有了，会再发一套重复的）

写前自动备份；发完重启服务端、重新选角生效。`)) return;
  await giveOp('/api/loadout/apply', { characNo: state.characterId }, '整套已发放');
  if (online) refreshMailbox();
}

/* ---------------- 任务材料补发 ---------------- */

async function questMaterials() {
  if (!state.characterId) { toast('先选角色', 'error'); return; }
  const online = onlineGive();
  await giveOp('/api/quests/materials', { characNo: state.characterId }, '材料已按缺口补发');
  if (online) refreshMailbox();
  refreshQuests();
}

/* ---------------- GM 名单页 ---------------- */

async function refreshGmList() {
  try {
    const body = await api('/api/gmlist');
    if (body.error) {
      toast(body.error, 'error');
      return;
    }
    state.gmlist = body;
  } catch (e) {
    toast('读取 GM 名单失败：' + e.message, 'error');
    return;
  }
  renderGmList();
}

function renderGmList() {
  const rows = state.gmlist.rows.map((row) =>
    `<tr class="clickable${state.gmlist.selected === row.mId ? ' selected' : ''}" data-gm="${row.mId}">
      <td class="id">${row.mId}</td>
      <td>${esc(row.userName || '—')}</td>
      <td class="num">${row.level}</td>
    </tr>`);
  $('#gmTable').innerHTML = table(
    [{ label: '账号编号', num: true }, { label: '账号名' }, { label: 'GM 等级', num: true }],
    rows, { fixed: true, empty: '名单是空的 —— 上面填账号名（本包默认 123456789）加进来' });
  // 账号名联想：库里已有账号（member_info）都列进 datalist，免得用户把名字当编号填
  const accounts = state.gmlist.accounts || [];
  $('#gmAccountList').innerHTML = accounts.map((a) =>
    `<option value="${esc(a.userId || a.userName || a.mId)}">账号编号 ${a.mId}${a.userName && a.userName !== a.userId ? ' · ' + esc(a.userName) : ''}</option>`).join('');
}

/* ---------------- 命令簿页 ---------------- */

function renderCommands() {
  const rows = GM_COMMANDS.map(([cmd, what]) =>
    `<tr class="clickable" data-cmd="${esc(cmd)}">
      <td style="font-family:var(--mono)">${esc(cmd)}</td>
      <td class="muted">${esc(what)}</td>
      <td style="width:60px"><span class="tag">复制</span></td>
    </tr>`);
  $('#commandTable').innerHTML = table(
    [{ label: '命令（点一行复制）' }, { label: '说明' }, { label: '' }],
    rows, { empty: '没有命令' });
}

/* ---------------- 环境页 ---------------- */

function renderEnvForm() {
  const s = state.status;
  if (!s) return;
  $('#envForm').innerHTML = `
    <label class="wide">数据库（s1a5.db）<input type="text" data-env="db" value="${esc(s.db || '')}"></label>
    <label class="wide">PVF（Script.pvf）<input type="text" data-env="pvf" value="${esc(s.pvf || '')}"></label>
    <label class="wide">图标库（ImagePacks* 目录，多个用 ; 分隔）<input type="text" data-env="packs" value="${esc(s.imagepacks || '')}"></label>
    <label>游戏端口（写闸探测）<input type="number" data-env="gamePort" value="${s.gamePort}" min="1" max="65535"></label>`;
}

/* ---------------- 邮箱（在线发放的投递箱） ---------------- */

async function refreshMailbox() {
  if (!state.characterId) {
    state.mailbox = [];
    renderMailboxIfActive();
    return;
  }
  try {
    const body = await api('/api/mailbox?characNo=' + state.characterId);
    if (body.error) {
      toast(body.error, 'error');
      return;
    }
    state.mailbox = body.rows || [];
  } catch (e) {
    toast('读取邮箱失败：' + e.message, 'error');
    return;
  }
  renderMailboxIfActive();
}

/** 只有当前正停在「背包 / 穿戴」页的「邮箱」分支时才重画 —— 别的页面调它别乱改别处。 */
function renderMailboxIfActive() {
  if (state.page === 'inventory' && state.inventory.container === MAILBOX_CONTAINER) {
    renderMailbox();
  }
}

function renderMailbox() {
  // 邮箱现在是「背包 / 穿戴」页里的一个伪容器（CONTAINERS 之后那个「邮箱」chip）——
  // 复用背包页那两张表位，不另开面板。
  // 切换条也要重画：选中状态跟着 inv.container 走，不重画的话会停在「背包」上一起亮（用户上报的 bug）。
  renderContainerChips();
  $('#invToolbar').style.display = 'none';
  $('#inventoryActions').innerHTML = '';
  $('#inventoryNote').style.display = '';
  $('#inventoryMeta').textContent = state.characterId
    ? `${currentCharacter() ? currentCharacter().name : ''} 的邮箱`
    : '没选角色';
  const rows = (state.mailbox || []).map((row) =>
    `<tr>
      <td class="num">${row.postalId}</td>
      <td class="id">${row.itemId}</td>
      <td><div class="icon-cell">${iconBox(row.itemId)}<span class="name-cell" style="${rarityStyle(row.rarity)}">${esc(row.name || '目录外 ' + row.itemId)}</span></div></td>
      <td class="num">${row.quantity ? num(row.count) : '—'}</td>
      <td class="num">${row.upgrade ? '+' + row.upgrade : '—'}</td>
      <td class="num">${row.endurance || '—'}</td>
      <td>${esc(row.sender || '—')}</td>
      <td>${row.unlimited ? '<span class="tag ok">不过期</span>' : '<span class="tag warn">限时</span>'}</td>
      <td><button class="btn btn-danger btn-tiny" data-mail-del="${row.postalId}">删除</button></td>
    </tr>`);
  $('#inventoryTable').innerHTML = table(
    [{ label: '邮件号', num: true }, { label: 'ID', num: true }, { label: '名称' },
     { label: '数量', num: true }, { label: '强化', num: true }, { label: '耐久', num: true },
     { label: '寄件人' }, { label: '期限' }, { label: '', width: 70 }],
    rows, { fixed: true, empty: state.characterId
      ? '邮箱是空的 —— 服务端在跑时发东西会投到这里；离线发放直接进背包，不走邮箱'
      : '先选角色' });
}

/* ---------------- 写操作 ---------------- */

async function writeOp(action, body, successText) {
  const st = state.status;
  // 新服务端会自动识别"角色在不在游戏里"，所以这里**不预拦** —— 交给后端按目标角色精判
  // （前端只知道"有人在游戏里"，不知道你要改的那个角色在不在里面）。老服务端（无自动识别）
  // 才用粗判在跑就拦，避免白跑一趟。
  if (st && st.serverUp && !st.writeAllowed && !st.autoDetect) {
    toast('服务端正在运行 —— 写库被拒绝。三种做法：① 启动器【停止服务端】改完再启动；'
      + '② 游戏里小退到【角色选择界面】后再改；③ 发物品走邮箱（发放物品页）。', 'error');
    return;
  }
  await sendWrite(action, body, successText);
}

/**
 * 发放类操作：服务端在跑也能做 —— 后端把物品投进邮箱（在线），
 * 停了则直写背包（离线，写前自动备份）。所以这里不做写闸拦截。
 */
async function giveOp(action, body, successText) {
  await sendWrite(action, body, successText);
}

async function sendWrite(action, body, successText) {
  try {
    setStatus('写入中…');
    const result = await post(action, body);
    if (result.ok) {
      toast(successText || result.message || '已写入', 'ok');
    } else {
      toast(result.error || '写入失败', 'error');
    }
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    setStatus('就绪');
  }
  refreshStatus(true);
  if (state.page === 'environment') renderEnvLog();
}

/** 服务端在跑 = 发放走邮箱。 */
function onlineGive() {
  return !!(state.status && state.status.serverUp);
}

function renderEnvLog() {
  renderLog($('#envLog'));
}

/* ---------------- 事件绑定 ---------------- */

function bindEvents() {
  // 导航
  $$('.nav-item').forEach((btn) => btn.addEventListener('click', () => {
    $$('.nav-item').forEach((b) => b.classList.remove('active'));
    btn.classList.add('active');
    state.page = btn.dataset.page;
    $$('.page').forEach((p) => p.classList.remove('active'));
    const page = $(`.page[data-page="${state.page}"]`);
    if (page) page.classList.add('active');
    onEnterPage();
  }));

  // 角色选择
  $('#characterSelect').addEventListener('change', (e) => {
    state.characterId = Number(e.target.value);
    state.characterDetail = null;
    state.mailbox = [];
    state.inventory.selected = null;
    refreshCharacterDetail().catch(() => {});
    // 正停在背包页（含「邮箱」分支）时，换了角色就把这一页重读一遍
    if (state.page === 'inventory') refreshInventory();
  });

  // 换基础职业：立刻把「一转」「觉醒」下拉换成新职业的分支表（还没保存也能先选好看效果）。
  const jobSelEl = $('#jobSelect');
  if (jobSelEl) {
    jobSelEl.addEventListener('change', (e) => {
      fillBranchSelects(Number(e.target.value), 0, 0);   // 新职业默认"未转职 + 未觉醒"
    });
  }

  // 「小退」开关：**只对老服务端**（不支持自动识别）需要用户手点确认。
  // 新服务端（autoDetect）自动判断，按钮已 disabled，这里也就不会再触发。
  $('#shutToggle').addEventListener('click', async () => {
    if (state.status && state.status.autoDetect) {
      toast('这份服务端会自动识别小退，不需要手动开关。', 'ok');
      return;
    }
    const cur = !!(state.status && state.status.shutToSelect);
    const next = !cur;
    if (next) {
      const ok = confirm('确认角色现在停在【角色选择界面】吗？（没有进入任何角色）\n\n'
        + '只有停在选择界面时，服务端才已经把背包/技能/数值从内存里存好并释放；'
        + '此时直接改库不会丢。角色一旦进游戏请回来关掉这个开关。');
      if (!ok) return;
    }
    try {
      const r = await post('/api/shut-to-select', { on: next });
      toast(r.message || (next ? '已允许写库（已小退）' : '已关闭写库放行'), 'ok');
      await refreshStatus(true);
    } catch (e) {
      toast('切换失败：' + e.message, 'error');
    }
  });

  // 事件委托：data-action
  document.addEventListener('click', (e) => {
    const actionBtn = e.target.closest('[data-action]');
    if (actionBtn) {
      handleAction(actionBtn.dataset.action, actionBtn);
      return;
    }
    // 目录分类（rail 第一层）：切换分类，重置部位/材质/职业
    const kindBtn = e.target.closest('#kindChips [data-kind]');
    if (kindBtn) {
      const id = kindBtn.dataset.kind || null;
      state.catalog.kind = state.catalog.kind === id ? null : id;
      state.catalog.part = null;
      state.catalog.subtype = null;
      state.catalog.job = null;
      state.catalog.page = 1;
      refreshCatalog();
      renderKindRail();
      return;
    }
    // 目录部位（rail 第二层）：切换部位，重置材质（并重填顶部「类型/材质」下拉）
    const partBtn = e.target.closest('#kindChips [data-part]');
    if (partBtn) {
      const id = partBtn.dataset.part || null;
      state.catalog.part = state.catalog.part === id ? null : id;
      state.catalog.subtype = null;
      state.catalog.page = 1;
      refreshCatalog();
      renderKindRail();
      return;
    }
    // 目录行选中
    const itemRow = e.target.closest('[data-item]');
    if (itemRow) {
      const id = Number(itemRow.dataset.item);
      state.selectedItem = state.catalog.items.find((it) => it.id === id) || null;
      hideHoverCard();
      renderCatalog();
      return;
    }
    // 翻页
    const pageBtn = e.target.closest('[data-page]');
    if (pageBtn && pageBtn.closest('#catalogPager')) {
      state.catalog.page = Number(pageBtn.dataset.page);
      refreshCatalog();
      return;
    }
    // 容器切换（含「邮箱」伪容器 99）
    const containerBtn = e.target.closest('[data-container]');
    if (containerBtn) {
      state.inventory.container = Number(containerBtn.dataset.container);
      state.inventory.selected = null;
      refreshInventory();
      return;
    }
    // 强行删除一封邮件（邮箱分支里的「删除」按钮）
    const mailDel = e.target.closest('[data-mail-del]');
    if (mailDel) {
      const postalId = Number(mailDel.dataset.mailDel);
      const row = (state.mailbox || []).find((m) => m.postalId === postalId);
      const what = row ? `${row.name || row.itemId}（#${row.itemId}）` : ('邮件 ' + postalId);
      if (confirm(`强行删除这封邮件：${what}？\n\n`
        + '这会直接把这行标成已删除（不是"领取"）—— 发错了导致进不去游戏时用它清掉，'
        + '不必等邮件过期。写前自动备份整库。')) {
        // 这个监听器不是 async（document 级委托），用 promise 链而不是 await。
        writeOp('/api/mailbox/delete', { characNo: state.characterId, postalId }, '邮件已删除')
          .then(() => refreshMailbox());
      }
      return;
    }
    // 背包行选中
    const invRow = e.target.closest('[data-invslot]');
    if (invRow) {
      const slot = Number(invRow.dataset.invslot);
      state.inventory.selected = state.inventory.selected === slot ? null : slot;
      renderInventory();
      return;
    }
    // 任务行选中
    const questRow = e.target.closest('[data-quest]');
    if (questRow) {
      $$('#questTable tr').forEach((tr) => tr.classList.remove('selected'));
      questRow.classList.add('selected');
      state.selectedQuest = Number(questRow.dataset.quest);
      return;
    }
    // GM 名单行选中
    const gmRow = e.target.closest('[data-gm]');
    if (gmRow) {
      state.gmlist.selected = Number(gmRow.dataset.gm);
      renderGmList();
      return;
    }
    // 时装删除
    const avatarDel = e.target.closest('[data-avatar-delete]');
    if (avatarDel) {
      const uiId = Number(avatarDel.dataset.avatarDelete);
      if (confirm(`删除这件时装（编号 ${uiId}）？（写前自动备份）`)) {
        writeOp('/api/avatars/delete', { characNo: state.characterId, uiId }, '已删除').then(refreshAvatars);
      }
      return;
    }
    // 时装词条保存
    const avatarSave = e.target.closest('[data-avatar-save]');
    if (avatarSave) {
      const uiId = Number(avatarSave.dataset.avatarSave);
      const sel = document.querySelector(`[data-avatar-option="${uiId}"]`);
      if (sel && sel.value !== '') {
        writeOp('/api/avatars/option', {
          characNo: state.characterId, uiId, option: Number(sel.value)
        }, '时装词条已保存').then(refreshAvatars);
      }
      return;
    }
    // 技能保存
    const skillSave = e.target.closest('[data-skill-save]');
    if (skillSave) {
      const slot = Number(skillSave.dataset.skillSave);
      const input = document.querySelector(`.skill-level[data-slot="${slot}"]`);
      if (input) {
        writeOp('/api/skills/set', {
          characNo: state.characterId, slot,
          skillIndex: Number(input.dataset.index),
          level: Number(input.value)
        }, '技能已保存').then(refreshSkills);
      }
      return;
    }
    // 命令复制
    const cmdRow = e.target.closest('[data-cmd]');
    if (cmdRow) {
      navigator.clipboard.writeText(cmdRow.dataset.cmd)
        .then(() => toast('已复制：' + cmdRow.dataset.cmd, 'ok'))
        .catch(() => toast('复制失败（浏览器不给剪贴板权限）', 'error'));
      return;
    }
  });

  // 目录搜索（停顿即搜）
  let searchTimer = null;
  $('#itemQuery').addEventListener('input', (e) => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      state.catalog.query = e.target.value;
      state.catalog.page = 1;
      refreshCatalog();
    }, 300);
  });
  $('#itemMinLevel').addEventListener('change', (e) => { state.catalog.min = e.target.value; state.catalog.page = 1; refreshCatalog(); });
  $('#itemMaxLevel').addEventListener('change', (e) => { state.catalog.max = e.target.value; state.catalog.page = 1; refreshCatalog(); });
  $('#itemRarity').addEventListener('change', (e) => { state.catalog.rarity = Number(e.target.value); state.catalog.page = 1; refreshCatalog(); });
  $('#itemJob').addEventListener('change', (e) => { state.catalog.job = e.target.value || null; state.catalog.page = 1; refreshCatalog(); });
  $('#itemSubtype').addEventListener('change', (e) => { state.catalog.subtype = e.target.value || null; state.catalog.page = 1; refreshCatalog(); });

  // 物品悬停预览（大图 + 详情段落）：只在目录表的名称列上出卡，带 200ms 延迟
  const catalogTable = $('#catalogTable');
  catalogTable.addEventListener('mouseover', onCatalogOver);
  catalogTable.addEventListener('mouseout', onCatalogOut);
  catalogTable.addEventListener('scroll', hideHoverCard);
  $('#catalogPager').addEventListener('click', hideHoverCard);
  window.addEventListener('scroll', hideHoverCard, true);

  // 背包筛选
  let invTimer = null;
  $('#invQuery').addEventListener('input', (e) => {
    clearTimeout(invTimer);
    invTimer = setTimeout(() => { state.inventory.query = e.target.value; renderInventory(); }, 250);
  });
  $('#invRarity').addEventListener('change', (e) => { state.inventory.rarity = Number(e.target.value); renderInventory(); });
}

async function handleAction(action, btn) {
  const characNo = Number(state.characterId);
  switch (action) {
    case 'refresh-status': refreshStatus(); break;
    case 'refresh-character': refreshCharacterDetail().catch(() => {}); break;
    case 'refresh-inventory': refreshInventory(); break;
    case 'refresh-avatar': refreshAvatars(); break;
    case 'refresh-skills': refreshSkills(); break;
    case 'refresh-quest': refreshQuests(); break;
    case 'loadout-preview': await loadoutPreview(); break;
    case 'loadout-apply': await loadoutApply(); break;
    case 'quest-materials': await questMaterials(); break;
    case 'refresh-gmlist': refreshGmList(); break;
    case 'clear-catalog-filters':
      state.catalog.query = ''; state.catalog.kind = null; state.catalog.part = null;
      state.catalog.subtype = null; state.catalog.job = null;
      state.catalog.rarity = -1; state.catalog.min = ''; state.catalog.max = '';
      state.catalog.page = 1;
      $('#itemQuery').value = ''; $('#itemMinLevel').value = ''; $('#itemMaxLevel').value = '';
      $('#itemRarity').value = '-1'; $('#itemJob').value = ''; $('#itemSubtype').value = '';
      refreshCatalog(); renderKindRail();
      break;
    case 'clear-inv-filters':
      state.inventory.query = ''; state.inventory.rarity = -1;
      $('#invQuery').value = ''; $('#invRarity').value = '-1';
      renderInventory();
      break;
    case 'save-character': {
      const detail = state.characterDetail;
      if (!detail) { toast('还没读到角色数据 —— 先点【重新读取】', 'error'); break; }
      const read = (key) => Number(document.querySelector(`#characterForm [data-field="${key}"]`)?.value ?? 0);
      const lev = read('lev');
      const sp = read('remainSp');
      const money = read('money');
      const cera = read('cera');
      const winPoint = read('winPoint');
      const todo = [];
      if (lev !== Number(detail.lev)) todo.push(['/api/character/level', { characNo, level: lev }, '等级已保存（累计经验与 SP 已按经验表重算）']);
      if (sp !== Number(detail.remainSp)) todo.push(['/api/character/sp', { characNo, sp }, 'SP 已保存']);
      if (money !== Number(detail.money)) todo.push(['/api/character/money', { characNo, money }, '金币已保存']);
      if (cera !== Number(detail.cera)) todo.push(['/api/character/cera', { mId: detail.mId, cera }, '点券已保存']);
      if (winPoint !== Number(detail.winPoint)) todo.push(['/api/character/winpoint', { characNo, winPoint }, '决斗胜点已保存']);
      if (!todo.length) { toast('没有改动的项 —— 数值和库里的当前值一样', 'ok'); break; }
      for (const entry of todo) {
        await writeOp(entry[0], entry[1], entry[2]);
        // 服务端在跑且**不支持自动识别**、也没被手动放行：第一条就被拒，后面不用再打。
        // 自动识别的服务端不在这里退出 —— 每条都会按自己的目标角色精判（可能角色项被拒、
        // 点券项放行这种混合情况），逐条试才准。
        const st = state.status;
        if (st && st.serverUp && !st.writeAllowed && !st.autoDetect) break;
      }
      await refreshCharacterDetail().catch(() => {});
      break;
    }
    case 'recalc-level': {
      const level = Number(document.querySelector('#characterForm [data-field="lev"]')?.value || 0);
      if (!confirm(`按 ${level} 级重算累计经验？

只把累计经验夹回该等级的合法区间（该级起点 ~ 下一级起点），等级不变、本级已攒的经验不会丢；经验本来就正常时会提示"不用重算"。`)) break;
      await writeOp('/api/character/level', { characNo, level }, '累计经验已按等级重算');
      await refreshCharacterDetail().catch(() => {});
      break;
    }
    case 'save-sp':
      await writeOp('/api/character/sp', { characNo, sp: Number($('#spInput')?.value || 0) }, 'SP 已保存');
      break;
    case 'recover-fatigue':
      await writeOp('/api/character/fatigue', { characNo }, '疲劳已恢复');
      break;
    case 'save-class': {
      // 一次保存「基础职业 + 一转 + 觉醒」。换职业时一转/觉醒会自动清零（旧分支在新职业上无意义）。
      const d = state.characterDetail;
      if (!d) { toast('还没读到角色数据 —— 先点【重新读取】', 'error'); break; }
      const job = Number($('#jobSelect')?.value ?? d.job);
      const first = Number($('#growFirst')?.value ?? 0);
      const second = Number($('#growSecond')?.value ?? 0);
      const packed = ((second & 0x07) << 4) | (first & 0x0F);
      if (job < 0 || job > 5) { toast('基础职业只能是 0..5', 'error'); break; }
      if (job === Number(d.job) && packed === Number(d.growType)) {
        toast('职业与转职都没变 —— 和库里的当前值一样', 'ok'); break;
      }
      const jobChanged = job !== Number(d.job);
      const jobName = JOB_NAMES[job] || ('职业' + job);
      const label = growText(packed, job);
      let msg = `保存职业 / 转职：\n基础职业 ${job}（${jobName}）`;
      if (jobChanged) msg += '  ← 变了，一转/觉醒按新职业重建';
      msg += `\n一转 ${first}（${label}）`;
      if (second > 0) msg += `、觉醒 ${second}`;
      msg += `\n\n转职值将写为 ${packed}。这会按「最终职业 + 最终一转/觉醒」整套重建技能表、SP 按等级重算`
           + `（等同服务端 #grow / 建一个该职业的新角色），重新选角生效，不需要再敲 #clear all skill。`;
      if (confirm(msg)) {
        await writeOp('/api/character/class', { characNo, job, firstGrow: first, secondGrow: second },
          `已保存：${jobName} · ${label}${second > 0 ? ' · 觉醒 ' + second : ''}（技能表已重建）`);
        await refreshCharacterDetail().catch(() => {});
      }
      break;
    }
    case 'give-item': {
      const item = state.selectedItem;
      if (!item) break;
      const give = (name) => document.querySelector(`[data-give="${name}"]`);
      if (item.kind === 'avatar') {
        await giveOp('/api/avatars/give', {
          characNo, itemId: item.id,
          days: Number(give('avatarDays')?.value || 0),
          abilityNo: Number(give('avatarAbility')?.value || 0)
        }, onlineGive() ? '时装已发放（写进装扮栏，重新选角可见）' : '时装已发放');
      } else {
        await giveOp('/api/give', {
          characNo, itemId: item.id,
          count: Number(give('count')?.value || 1),
          enhance: Number(give('enhance')?.value || 0),
          endurance: Number(give('endurance')?.value || 0)
        }, onlineGive() ? '' : '已发放');   // 在线时不写死文案：后端知道角色在不在游戏里，用它的提示
      }
      if (onlineGive()) refreshMailbox();
      break;
    }
    case 'inv-save-eq': {
      const slot = state.inventory.selected;
      if (slot === null || slot === undefined) break;
      const edit = (name) => document.querySelector(`[data-edit="${name}"]`);
      const enhance = Number(edit('enhance')?.value || 0);
      const endurance = Number(edit('endurance')?.value || 0);
      await writeOp('/api/inventory/edit', { characNo, container: state.inventory.container, slot, op: 'enhance', value: enhance }, '强化已保存');
      if (endurance > 0) {
        await writeOp('/api/inventory/edit', { characNo, container: state.inventory.container, slot, op: 'endurance', value: endurance }, '耐久已保存');
      }
      break;
    }
    case 'inv-save-enchant': {
      const slot = state.inventory.selected;
      if (slot === null || slot === undefined) break;
      await writeOp('/api/inventory/edit', { characNo, container: state.inventory.container, slot, op: 'enchant', value: Number(document.querySelector('[data-edit="enchant"]')?.value || 0) }, '附魔已保存');
      break;
    }
    case 'inv-save-addinfo': {
      const slot = state.inventory.selected;
      if (slot === null || slot === undefined) break;
      await writeOp('/api/inventory/edit', { characNo, container: state.inventory.container, slot, op: 'addinfo', value: Number(document.querySelector('[data-edit="addinfo"]')?.value || 0) }, '附加信息已保存');
      break;
    }
    case 'inv-delete': {
      const slot = state.inventory.selected;
      if (slot === null || slot === undefined) break;
      if (confirm(`删除容器 ${state.inventory.container} 槽 ${slot} 的物品？（写前自动备份）`)) {
        await writeOp('/api/inventory/edit', { characNo, container: state.inventory.container, slot, op: 'delete', value: 0 }, '已删除');
        state.inventory.selected = null;
        refreshInventory();
      }
      break;
    }
    case 'quest-ready': {
      if (!state.selectedQuest) { toast('先在表格里点一个任务', 'error'); break; }
      await writeOp('/api/quests/ready', { characNo, ids: [state.selectedQuest] }, '已标记可交（进度清零）');
      break;
    }
    case 'quest-clear': {
      if (!state.selectedQuest) { toast('先在表格里点一个任务', 'error'); break; }
      if (confirm(`任务 ${state.selectedQuest} 标成已通关？（不发奖励；已完成表重选角色才收）`)) {
        await writeOp('/api/quests/clear', { characNo, ids: [state.selectedQuest], all: false }, '已标成已通关');
      }
      break;
    }
    case 'quest-force': {
      if (!state.selectedQuest) { toast('先在表格里点一个任务', 'error'); break; }
      if (confirm(`任务 ${state.selectedQuest} 强制完成？（移出进行中 + 写完成标记，不发奖励）`)) {
        await writeOp('/api/quests/force', { characNo, ids: [state.selectedQuest] }, '已强制完成');
      }
      break;
    }
    case 'gm-add': {
      const account = $('#gmAddId')?.value.trim();
      if (!account) { toast('填账号名（如 123456789）或它的账号编号', 'error'); break; }
      // 不传 successText：后端会回「账号「xxx」(账号编号 N) 加入 GM 名单」这种带真编号的消息，
      // 用户能一眼看出填的名字被认成了哪个编号（这正是这次要修的坑）。
      await writeOp('/api/gmlist/add', { account, level: 1 });
      break;
    }
    case 'gm-remove': {
      const mId = state.gmlist.selected;
      if (!mId) { toast('先在表格里点一个账号', 'error'); break; }
      if (confirm(`把账号 ${mId} 移出 GM 名单？`)) {
        await writeOp('/api/gmlist/remove', { mId }, `账号 ${mId} 已移出（重启服务端生效）`);
      }
      break;
    }
    case 'copy-character': {
      const name = $('#copyCharName')?.value.trim();
      if (!name) { toast('新角色名不能为空', 'error'); break; }
      if (!confirm(`把当前角色整份拷成「${name}」？（离线写库、写前自动备份；要重新登录才看得到新角色）`)) break;
      await writeOp('/api/character/copy', { characNo, newName: name }, '已复制');
      refreshCharacters();
      break;
    }
    case 'delete-character': {
      const characNo2 = Number($('#deleteCharSelect')?.value || 0);
      const name = $('#deleteCharName')?.value.trim();
      if (!characNo2 || !name) { toast('选角色、把名字打一遍', 'error'); break; }
      if (!confirm(`删除角色 ${name}（${characNo2}）？（不可撤销、写前自动备份）`)) break;
      await writeOp('/api/character/delete', { characNo: characNo2, name }, '已删除');
      refreshCharacters();
      break;
    }
    case 'save-env': {
      const env = (name) => document.querySelector(`[data-env="${name}"]`);
      try {
        await post('/api/configure', {
          db: env('db')?.value.trim(),
          pvf: env('pvf')?.value.trim(),
          imagepacks: env('packs')?.value.trim(),
          gamePort: Number(env('gamePort')?.value || 10011)
        });
        toast('已应用（重读 PVF / 图标库）', 'ok');
        refreshStatus(true);
      } catch (e) {
        toast('应用失败：' + e.message, 'error');
      }
      break;
    }
    case 'reload-pvf': {
      try {
        await post('/api/reload-pvf', {});
        toast('PVF 已重读', 'ok');
        refreshStatus(true);
      } catch (e) {
        toast('重读失败：' + e.message, 'error');
      }
      break;
    }
    default: break;
  }
}

function onEnterPage() {
  switch (state.page) {
    case 'overview': renderOverview(); break;
    case 'character': refreshCharacterDetail().catch(() => {}); break;
    case 'give': refreshKinds(); refreshCatalog(); break;
    case 'inventory': refreshInventory(); break;
    case 'avatar': refreshAvatars(); break;
    case 'skills': refreshSkills(); break;
    case 'quest': refreshQuests(); break;
    case 'gmlist': refreshGmList(); break;
    case 'commands': renderCommands(); break;
    case 'environment': renderEnvForm(); renderLog($('#envLog')); break;
    default: break;
  }
}

/* ---------------- 启动 ---------------- */

async function boot() {
  const auth = await api('/api/csrf');
  csrfToken = auth.csrfToken;
  const logout = $('#logoutBtn');
  logout.hidden = !auth.authenticated;
  logout.addEventListener('click', async () => {
    try {
      const response = await fetch('/logout', { method: 'POST', headers: { 'X-A4-GM-CSRF': csrfToken } });
      if (!response.ok && response.status !== 401) throw new Error(`HTTP ${response.status}`);
      window.location.replace('/login');
    } catch (e) {
      toast('退出登录失败：' + e.message, 'error');
    }
  });
  bindEvents();
  $$('.nav-item')[0].classList.add('active');
  $$('.page')[0].classList.add('active');
  await refreshStatus(true);
  await refreshCharacters(true);
  renderOverview();
  renderCommands();
  // 目录在后台构建 —— 轮询到就绪为止
  const pollCatalog = setInterval(async () => {
    await refreshStatus(true);
    if (state.status && state.status.catalogReady) {
      clearInterval(pollCatalog);
      if (state.page === 'give') { refreshKinds(); refreshCatalog(); }
    }
  }, 1500);
  setTimeout(pollStop, 120000);
  function pollStop() { clearInterval(pollCatalog); }

  // 常态轻量轮询：只刷「服务端在不在跑 / 写库放没放行」，不动别的面板。
  // 目的有二：① 顶栏「已小退」开关的状态跟服务端保持一致（服务端重启/停止会自动复位）；
  // ② 用户改完数据后能看到写闸重新关上。太重的不做，免得把正在输入的表单打断。
  setInterval(refreshStatusQuiet, 5000);
}

/** 只拉 /api/status 并更新顶栏（胶囊 + 「已小退」开关 + 账号徽标），不重绘页面主体。 */
async function refreshStatusQuiet() {
  if (document.hidden) return;
  try {
    state.status = await api('/api/status');
  } catch (e) {
    return;
  }
  renderPills();
  renderStatusbar();
  renderAccountChip();
}

boot().catch(e => toast('GM 页面初始化失败：' + e.message, 'error'));
