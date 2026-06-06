<template>
  <main class="app-shell">
    <!-- Header -->
    <header class="app-header">
      <div class="brand-block">
        <div class="brand-mark">C♭</div>
        <div>
          <p class="eyebrow">Compiler Studio</p>
          <h1>C♭ 编译器实验平台</h1>
        </div>
      </div>
      <div class="header-meta">
        <span class="meta-pill"><FileCode2 :size="15" /> {{ codeStats.lines }} 行</span>
        <span class="meta-pill"><Activity :size="15" /> {{ codeStats.characters }} 字符</span>
        <button class="theme-toggle" :title="theme === 'dark' ? '切换浅色' : '切换深色'" @click="toggleTheme">
          <Sun v-if="theme === 'dark'" :size="17" />
          <Moon v-else :size="17" />
        </button>
      </div>
    </header>

    <!-- Pipeline -->
    <section class="pipeline-strip" aria-label="compiler pipeline">
      <div
        v-for="stage in pipelineStages"
        :key="stage.id"
        class="pipeline-step"
        :class="{ active: activeStage === stage.id, done: completedStages.includes(stage.id) }"
      >
        <span class="step-index">{{ stage.index }}</span>
        <span class="step-body">
          <span class="step-label">{{ stage.label }}</span>
          <span class="step-sub">{{ stage.sub }}</span>
        </span>
        <span class="step-check"><Check :size="16" /></span>
      </div>
    </section>

    <!-- Workspace -->
    <section class="workspace">
      <!-- Editor -->
      <div class="pane">
        <header class="pane-head">
          <div class="head-title">
            <span class="ico"><Code2 :size="18" /></span>
            <div>
              <p class="eyebrow">Source</p>
              <h2>代码编辑器</h2>
            </div>
          </div>
          <select v-model="selectedExample" class="example-select" @change="loadExample">
            <option v-for="item in examples" :key="item.name" :value="item.name">{{ item.name }}</option>
          </select>
        </header>

        <div class="toolbar">
          <button class="tool-btn" :disabled="loading" @click="handleAction('lexer')">
            <ListTree :size="16" /> 词法分析
          </button>
          <button class="tool-btn" :disabled="loading" @click="handleAction('parser')">
            <Braces :size="16" /> 语法分析
          </button>
          <button class="tool-btn" :disabled="loading" @click="handleAction('compile')">
            <Binary :size="16" /> 编译
          </button>
          <button class="tool-btn primary" :disabled="loading" @click="handleAction('run')">
            <Play :size="16" /> 编译并运行 <kbd>⌘↵</kbd>
          </button>
        </div>

        <CodeEditor v-model="code" :theme="theme" @run="handleAction('run')" />
      </div>

      <!-- Result -->
      <aside class="pane">
        <header class="pane-head">
          <div class="head-title">
            <span class="ico"><Terminal :size="18" /></span>
            <div>
              <p class="eyebrow">Output</p>
              <h2>{{ activeTabMeta.title }}</h2>
            </div>
          </div>
          <span class="status" :class="{ error: hasError, busy: loading }">
            <Loader2 v-if="loading" :size="14" class="spin" />
            <AlertTriangle v-else-if="hasError" :size="14" />
            <CheckCircle2 v-else :size="14" />
            {{ statusText }}
          </span>
        </header>

        <!-- Tabs -->
        <div class="tabs">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            class="tab"
            :class="{ active: activeTab === tab.id, empty: !results[tab.id] }"
            @click="activeTab = tab.id"
          >
            <component :is="tab.icon" :size="14" />
            {{ tab.label }}
            <span v-if="results[tab.id]" class="dot" />
          </button>
        </div>

        <!-- stdin -->
        <div class="stdin-wrap">
          <div class="stdin-label"><TerminalSquare :size="14" /> 标准输入</div>
          <textarea v-model="stdin" class="stdin" spellcheck="false" placeholder="scanf 后续版本启用…" />
        </div>

        <!-- output -->
        <div class="output-region" :class="{ 'is-light-content': activeTab === 'lexer' || activeTab === 'parser' }">
          <Transition name="fade" mode="out-in">
            <!-- error overrides -->
            <div v-if="errorPayload" key="err" class="error-card">
              <div class="error-head"><AlertTriangle :size="17" /> 编译错误</div>
              <div class="error-body">
                <div class="error-row"><span class="k">stage</span><span class="v">{{ errorPayload.stage }}</span></div>
                <div class="error-row"><span class="k">message</span><span class="v">{{ errorPayload.message }}</span></div>
                <div class="error-row"><span class="k">position</span><span class="v">line {{ errorPayload.line }}, col {{ errorPayload.column }}</span></div>
              </div>
            </div>

            <!-- empty -->
            <div v-else-if="!results[activeTab]" key="empty" class="placeholder">
              <component :is="activeTabMeta.icon" :size="40" />
              <div>点击工具栏按钮，结果会显示在这里。</div>
            </div>

            <!-- lexer -->
            <div v-else-if="activeTab === 'lexer'" key="lexer" class="token-view">
              <div class="token-summary">
                <span class="token-chip">总计 <b>{{ lexerTree.total }}</b></span>
                <span v-for="g in lexerTree.groups" :key="g.name" class="token-chip">
                  {{ g.short }} <b>{{ g.tokens.length }}</b>
                </span>
              </div>
              <div class="token-groups">
                <div v-for="g in lexerTree.groups" :key="g.name" class="token-group">
                  <div class="token-group-head">
                    <span>{{ g.label }}</span>
                    <span class="count">{{ g.tokens.length }}</span>
                  </div>
                  <div class="token-cells">
                    <div v-for="t in g.tokens" :key="t.key" class="token-cell" :title="t.type">
                      <span class="lex">{{ t.lexeme || 'EOF' }}</span>
                      <span class="typ">{{ t.type }}</span>
                      <span class="pos">{{ t.line }}:{{ t.column }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- parser / AST -->
            <div v-else-if="activeTab === 'parser'" key="parser" class="ast-view">
              <AstTree :node="results.parser" />
            </div>

            <!-- compile / IR -->
            <pre v-else-if="activeTab === 'compile'" key="compile" class="code-out">{{ results.compile }}</pre>

            <!-- run -->
            <div v-else key="run" class="run-grid">
              <div class="run-block" :class="{ empty: !results.run.stdout }">
                <div class="run-block-head"><Terminal :size="13" /> stdout</div>
                <pre>{{ results.run.stdout || '(empty)' }}</pre>
              </div>
              <div v-if="results.run.stderr" class="run-block">
                <div class="run-block-head"><AlertTriangle :size="13" /> stderr</div>
                <pre>{{ results.run.stderr }}</pre>
              </div>
              <div class="run-block exit" :class="{ nonzero: results.run.exitCode !== 0 }">
                <div class="run-block-head"><CornerDownRight :size="13" /> exit code</div>
                <pre>{{ results.run.exitCode }}</pre>
              </div>
              <div class="run-block">
                <div class="run-block-head"><Binary :size="13" /> 三地址码 IR</div>
                <pre>{{ results.run.ir }}</pre>
              </div>
            </div>
          </Transition>
        </div>
      </aside>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  Activity, AlertTriangle, Binary, Braces, Check, CheckCircle2, Code2,
  CornerDownRight, FileCode2, ListTree, Loader2, Moon, Play, Sun,
  Terminal, TerminalSquare
} from 'lucide-vue-next'
import CodeEditor from './components/CodeEditor.vue'
import AstTree from './components/AstTree.vue'
import * as compilerApi from './api/compilerApi'

/* ---------- theme ---------- */
const theme = ref('dark')
function applyTheme(t) {
  theme.value = t
  document.documentElement.setAttribute('data-theme', t)
  localStorage.setItem('cflat-theme', t)
}
function toggleTheme() {
  applyTheme(theme.value === 'dark' ? 'light' : 'dark')
}
onMounted(() => {
  const saved = localStorage.getItem('cflat-theme')
  applyTheme(saved === 'light' ? 'light' : 'dark')
})

/* ---------- pipeline ---------- */
const pipelineStages = [
  { id: 'lexer', index: '01', label: '词法分析', sub: 'Lexer' },
  { id: 'parser', index: '02', label: '语法分析', sub: 'Parser → AST' },
  { id: 'semantic', index: '03', label: '语义检查', sub: 'Semantic' },
  { id: 'ir', index: '04', label: '中间代码', sub: 'IR Gen' },
  { id: 'vm', index: '05', label: '虚拟机运行', sub: 'VM' }
]

/* ---------- tabs ---------- */
const tabs = [
  { id: 'lexer', label: 'Tokens', icon: ListTree },
  { id: 'parser', label: 'AST', icon: Braces },
  { id: 'compile', label: 'IR', icon: Binary },
  { id: 'run', label: '运行', icon: Play }
]
const tabMeta = {
  lexer: { title: '词法分析 · Tokens', icon: ListTree },
  parser: { title: '语法树 · AST', icon: Braces },
  compile: { title: '三地址码 · IR', icon: Binary },
  run: { title: '程序运行结果', icon: Play }
}
const activeTab = ref('run')
const activeTabMeta = computed(() => tabMeta[activeTab.value])

/* ---------- examples ---------- */
const examples = [
  {
    name: '循环与数组',
    code: `int main() {
    int arr[5];
    int i = 0;
    int sum = 0;

    while (i < 5) {
        arr[i] = i + 1;
        sum = sum + arr[i];
        i = i + 1;
    }

    printf(sum);
    return 0;
}`
  },
  {
    name: '函数调用',
    code: `int add(int a, int b) {
    return a + b;
}

int main() {
    int result = add(2, 3);
    printf(result);
    return 0;
}`
  },
  {
    name: 'for 与 continue',
    code: `int main() {
    int i;
    int sum = 0;

    for (i = 0; i < 10; i = i + 1) {
        if (i == 5) {
            continue;
        }
        sum = sum + i;
    }

    printf(sum);
    return 0;
}`
  },
  {
    name: '递归 · 斐波那契',
    code: `int fib(int n) {
    if (n < 2) {
        return n;
    }
    return fib(n - 1) + fib(n - 2);
}

int main() {
    printf(fib(10));
    return 0;
}`
  },
  {
    name: '条件分支',
    code: `int max(int a, int b) {
    if (a > b) {
        return a;
    }
    return b;
}

int main() {
    int x = 7;
    int y = 12;
    printf(max(x, y));
    return 0;
}`
  }
]

const selectedExample = ref(examples[0].name)
const code = ref(examples[0].code)
const stdin = ref('')
const loading = ref(false)
const hasError = ref(false)
const errorPayload = ref(null)
const activeStage = ref('')
const completedStages = ref([])

/* results cache per tab */
const results = ref({ lexer: null, parser: null, compile: null, run: null })

const statusText = computed(() => {
  if (loading.value) return '处理中'
  return hasError.value ? '错误' : '就绪'
})

const codeStats = computed(() => ({
  lines: code.value.split('\n').length,
  characters: code.value.length
}))

const lexerTree = computed(() => {
  const raw = results.value.lexer
  const tokens = Array.isArray(raw)
    ? raw.map((t, i) => ({ ...t, index: i + 1, key: `${i}-${t.type}-${t.line}-${t.column}` }))
    : []
  const defs = [
    { name: 'keywords', label: '关键字 Keywords', short: '关键字', m: t => t.type.startsWith('KW_') },
    { name: 'identifiers', label: '标识符 Identifiers', short: '标识符', m: t => t.type === 'IDENTIFIER' },
    { name: 'literals', label: '字面量 Literals', short: '字面量', m: t => t.type.endsWith('_LITERAL') },
    { name: 'operators', label: '运算符 Operators', short: '运算符', m: t => ['PLUS','MINUS','STAR','SLASH','PERCENT','LT','LTE','GT','GTE','EQEQ','NEQ','ANDAND','OROR','BANG','ASSIGN'].includes(t.type) },
    { name: 'delimiters', label: '分隔符 Delimiters', short: '分隔符', m: t => ['LPAREN','RPAREN','LBRACE','RBRACE','LBRACKET','RBRACKET','SEMICOLON','COMMA'].includes(t.type) },
    { name: 'eof', label: '结束符 EOF', short: 'EOF', m: t => t.type === 'EOF' }
  ].map(g => ({ ...g, tokens: tokens.filter(g.m) }))
  return { total: tokens.length, groups: defs.filter(g => g.tokens.length > 0) }
})

function loadExample() {
  const found = examples.find(i => i.name === selectedExample.value)
  if (found) {
    code.value = found.code
    resetResults()
  }
}

function resetResults() {
  hasError.value = false
  errorPayload.value = null
  activeStage.value = ''
  completedStages.value = []
  results.value = { lexer: null, parser: null, compile: null, run: null }
}

async function handleAction(type) {
  loading.value = true
  hasError.value = false
  errorPayload.value = null
  activeStage.value = stageForAction(type)
  activeTab.value = type

  try {
    const result =
      type === 'lexer' ? await compilerApi.lex(code.value)
      : type === 'parser' ? await compilerApi.parse(code.value)
      : type === 'compile' ? await compilerApi.compile(code.value)
      : await compilerApi.run(code.value, stdin.value)

    if (!result.ok) {
      showError(result.payload)
      return
    }
    const p = result.payload
    if (type === 'lexer') {
      results.value.lexer = p.tokens
      completedStages.value = ['lexer']
    } else if (type === 'parser') {
      results.value.parser = p.ast
      completedStages.value = ['lexer', 'parser']
    } else if (type === 'compile') {
      results.value.compile = p.ir.join('\n')
      completedStages.value = ['lexer', 'parser', 'semantic', 'ir']
    } else {
      results.value.run = {
        stdout: p.stdout, stderr: p.stderr, exitCode: p.exitCode, ir: p.ir.join('\n')
      }
      completedStages.value = ['lexer', 'parser', 'semantic', 'ir', 'vm']
    }
  } catch (e) {
    showError({ stage: 'NETWORK', message: e.message || '请求失败，请确认后端已启动。', line: 0, column: 0 })
  } finally {
    loading.value = false
  }
}

function showError(payload) {
  hasError.value = true
  errorPayload.value = {
    stage: payload.stage || 'UNKNOWN',
    message: payload.message || 'Unknown error',
    line: payload.line ?? 0,
    column: payload.column ?? 0
  }
  completedStages.value = []
}

function stageForAction(type) {
  if (type === 'lexer') return 'lexer'
  if (type === 'parser') return 'parser'
  if (type === 'compile') return 'ir'
  return 'vm'
}
</script>
