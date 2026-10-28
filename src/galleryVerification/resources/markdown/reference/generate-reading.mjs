import fs from 'node:fs/promises';
import vm from 'node:vm';
import { createHash } from 'node:crypto';

// 以固定外部解析器实际执行结果生成测试期望。
const output = new URL('./reading-corpus.json', import.meta.url);
const corpus = JSON.parse(await fs.readFile(output, 'utf8'));
const pinned = [
    ['markdownItSha256', 'https://unpkg.com/markdown-it@14.2.0/dist/markdown-it.js'],
    ['katexSha256', 'https://unpkg.com/katex@0.16.21/dist/katex.js'],
    ['pluginSha256', 'https://unpkg.com/@vscode/markdown-it-katex@1.1.2/dist/index.js'],
];
const code = [];
for (const [key, url] of pinned) {
    const response = await fetch(url);
    if (!response.ok) throw new Error(url + ': HTTP ' + response.status);
    const text = await response.text();
    const hash = createHash('sha256').update(text).digest('hex');
    if (hash !== corpus.reference[key]) throw new Error('固定模块字节不匹配：' + key);
    code.push(text);
}
function loadCommonJs(source, extra = {}) {
    const context = { module: { exports: {} }, exports: {}, ...extra };
    vm.runInNewContext(source, context, { timeout: 15000 });
    return context.module.exports;
}
const MarkdownIt = loadCommonJs(code[0]);
const katex = loadCommonJs(code[1], { console });
const plugin = { exports: {}, require: name => {
    if (name !== 'katex') throw new Error('非预期测试依赖：' + name);
    return katex;
}, console };
vm.runInNewContext(code[2], plugin, { timeout: 15000 });
const md = new MarkdownIt({ html: true, breaks: false, linkify: true, typographer: false });
md.linkify.set({ fuzzyLink: false });
const options = { enableFencedBlocks: true, globalGroup: true, macros: {} };
md.core.ruler.push('reset-katex-macros', () => { options.macros = {}; });
md.use(plugin.exports.default, options);
function token(value) {
    return {
        type: value.type, tag: value.tag, nesting: value.nesting, attrs: value.attrs || [],
        content: value.content, info: value.info, hidden: value.hidden, map: value.map,
        children: value.children ? value.children.map(token) : [],
    };
}
for (const item of corpus.cases) {
    // 同时执行 renderer 核验行内数学入口，快照只保留后续 token 事实。
    md.render(item.source);
    item.tokens = md.parse(item.source, {}).map(token);
}
for (const item of corpus.acceptedDifferences) item.reference = md.parse(item.source, {}).map(token);
await fs.writeFile(output, JSON.stringify(corpus, null, 2) + '\n');
console.log('固定Markdown oracle完成：' + corpus.cases.length + '个对照用例；' + corpus.acceptedDifferences.length + '个明确差异');
