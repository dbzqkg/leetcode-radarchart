const API_URL = 'https://leetcode.cn/graphql/';
const LIMIT = 100;
const SLEEP_MS = 150;
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';

const COG_BENCHMARKS = [150, 150, 80, 100, 40, 60];
const DW = [0.2, 0.2, 0.1, 0.2, 0.1, 0.2];

const TIER_BENCHMARKS = {};
[['数组',60],['字符串',60],['哈希表',60],['动态规划',60],['数学',60],
 ['树',35],['二叉树',35],['深度优先搜索',35],['广度优先搜索',35],['二分查找',35],
 ['贪心',35],['排序',35],['双指针',35],['矩阵',35],['栈',35],
 ['递归',35],['链表',35],['队列',35],
].forEach(([k,v]) => TIER_BENCHMARKS[k] = v);
['并查集','单调栈','字典树','滑动窗口','图','设计','回溯','堆（优先队列）','前缀和','位运算',
 '拓扑排序','分治','线段树','模拟','计数','有序集合','快速选择','归并排序','最短路','字符串匹配',
 '组合数学','数据流','交互','扫描线','状态压缩','记忆化搜索','双向链表','二叉搜索树','平衡二叉树',
 '后缀数组','最小生成树','基环树','堆','图论','环检测','双连通分量','强连通分量','欧拉回路',
].forEach(t => TIER_BENCHMARKS[t] = 20);

const TAG_WEIGHTS = {};
[
  ['array','linked-list','stack','queue','hash-table','doubly-linked-list','matrix','ordered-set','heap-priority-queue'],
  ['tree','binary-tree','binary-search-tree','graph','minimum-spanning-tree','functional-graph','balanced-binary-tree'],
  ['trie','segment-tree','binary-indexed-tree','union-find','monotonic-stack','monotonic-queue','suffix-array','strongly-connected-component','biconnected-component','quickselect','merge-sort','counting-sort','bucket-sort','radix-sort','counting'],
  ['depth-first-search','breadth-first-search','binary-search','sorting','backtracking','divide-and-conquer','recursion','topological-sort','shortest-path','eulerian-circuit'],
  ['dynamic-programming','memoization','bitmask'],
  ['two-pointers','sliding-window','prefix-sum'],
  ['math','bit-manipulation','combinatorics','number-theory','hash-function'],
  ['greedy','brainteaser','geometry','game-theory','interactive','random','randomized','line-sweep','rejection-sampling','reservoir-sampling'],
  ['design','concurrency','simulation','database','shell','data-stream','iterator'],
  ['string','string-matching','rolling-hash'],
].forEach((tags, i) => {
  const w = [
    [0.7,0.1,0,0,0.2,0], [0.7,0.2,0.1,0,0,0], [0.4,0,0.6,0,0,0], [0.1,0.6,0.3,0,0,0],
    [0.1,0.4,0,0.5,0,0], [0.1,0.5,0,0.2,0,0.2], [0,0.1,0,0.8,0,0.1], [0,0,0,0.3,0,0.7],
    [0,0,0,0,1,0], [0.4,0.2,0.2,0,0.2,0],
  ][i];
  tags.forEach(t => TAG_WEIGHTS[t] = w);
});

function buildReq(cookie, body) {
  return { method: 'POST', headers: { 'Content-Type': 'application/json', Cookie: cookie, Referer: 'https://leetcode.cn/', Origin: 'https://leetcode.cn', 'User-Agent': UA }, body };
}

function diffScore(d) {
  if (d === 'HARD') return 5;
  if (d === 'MEDIUM') return 2.5;
  return 1;
}

async function crawl(cookie, send) {
  const map = new Map();
  const cog = [0,0,0,0,0,0];
  let totalQ = 0, loaded = 0, skip = 0, more = true;

  while (more) {
    const p = JSON.stringify({ operationName: 'problemsetQuestionList', variables: { categorySlug: '', skip, limit: LIMIT, filters: { status: 'AC' } }, query: 'query problemsetQuestionList($categorySlug: String, $limit: Int, $skip: Int, $filters: QuestionListFilterInput) { problemsetQuestionList(categorySlug: $categorySlug, limit: $limit, skip: $skip, filters: $filters) { hasMore total questions { difficulty topicTags { name nameTranslated slug } } } }' });
    const r = await fetch(API_URL, buildReq(cookie, p));
    if (r.status !== 200) { send({ status: 'ERROR', message: `凭证失效 [HTTP ${r.status}]` }); return; }
    const j = await r.json();
    if (j.errors) { send({ status: 'ERROR', message: `凭证失效 [${JSON.stringify(j.errors)}]` }); return; }
    const ps = j.data.problemsetQuestionList;
    more = ps.hasMore; totalQ = ps.total;
    for (const q of ps.questions) {
      loaded++;
      const d = q.difficulty, qs = diffScore(d);
      for (const t of (q.topicTags || [])) {
        const name = (t.nameTranslated && t.nameTranslated !== 'null') ? t.nameTranslated : t.name;
        if (!name) continue;
        let s = map.get(name);
        if (!s) { s = { name, slug: t.slug || '', total: 0, easy: 0, medium: 0, hard: 0 }; map.set(name, s); }
        s.total++; if (d === 'EASY') s.easy++; else if (d === 'MEDIUM') s.medium++; else s.hard++;
        const w = TAG_WEIGHTS[t.slug] || DW;
        for (let i = 0; i < 6; i++) cog[i] += qs * w[i];
      }
    }
    send({ status: 'RUNNING', message: `正在统计... (${loaded}/${totalQ})`, currentStep: loaded, totalSteps: totalQ });
    skip += LIMIT;
    await new Promise(rs => setTimeout(rs, SLEEP_MS));
  }

  for (const s of map.values()) {
    const sc = s.easy + s.medium * 2.5 + s.hard * 5;
    if (sc === 0) { s.mastery = 0; continue; }
    const b = TIER_BENCHMARKS[s.name] || 8;
    s.mastery = Math.round(1000 * (1 - Math.exp(-Math.log(2) / b * sc))) / 10;
  }

  const cognitive = {};
  for (let d = 0; d < 6; d++)
    cognitive[d] = Math.round(1000 * (1 - Math.exp(-Math.log(2) / COG_BENCHMARKS[d] * cog[d]))) / 10;

  const tags = [...map.values()].sort((a, b) => b.total - a.total);
  send({ status: 'SUCCESS', data: { total: totalQ, tags, cognitive } });
}

export async function onRequest(context) {
  const { request } = context;
  if (request.method !== 'POST')
    return new Response('Method Not Allowed', { status: 405 });

  let body;
  try { body = await request.json(); } catch (_) {
    return new Response(JSON.stringify({ status: 'ERROR', message: 'Invalid JSON' }), { status: 400, headers: { 'Content-Type': 'application/json' } });
  }
  const cookie = body.cookie || '';
  if (!cookie.includes('LEETCODE_SESSION'))
    return new Response(JSON.stringify({ status: 'ERROR', message: 'Cookie 中必须包含 LEETCODE_SESSION' }), { headers: { 'Content-Type': 'application/json' } });

  const { readable, writable } = new TransformStream();
  const writer = writable.getWriter();
  const enc = new TextEncoder();
  const send = (data) => { try { writer.write(enc.encode(`data: ${JSON.stringify(data)}\n\n`)); } catch (_) {} };

  send({ status: 'START' });

  context.waitUntil(
    crawl(cookie, send).finally(() => { try { writer.close(); } catch (_) {} })
  );

  return new Response(readable, {
    headers: { 'Content-Type': 'text/event-stream; charset=utf-8', 'Cache-Control': 'no-cache', 'Connection': 'keep-alive', 'X-Accel-Buffering': 'no' },
  });
}
