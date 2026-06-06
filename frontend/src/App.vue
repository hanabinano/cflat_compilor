<template>
  <main class="app-shell">
    <header class="app-header">
      <div class="brand-block">
        <div class="brand-mark">C♭</div>
        <div>
          <p class="eyebrow">Compiler Studio</p>
          <h1>C♭ 编译器课程展示台</h1>
        </div>
      </div>
      <div class="header-meta">
        <span class="meta-pill">
          <FileCode2 :size="15" />
          {{ codeStats.lines }} 行
        </span>
        <span class="meta-pill">
          <Activity :size="15" />
          {{ codeStats.characters }} 字符
        </span>
      </div>
    </header>

    <section class="pipeline-strip" aria-label="compiler pipeline">
      <div
        v-for="stage in pipelineStages"
        :key="stage.id"
        class="pipeline-step"
        :class="{ active: activeStage === stage.id, done: completedStages.includes(stage.id) }"
      >
        <span class="step-index">{{ stage.index }}</span>
        <span>{{ stage.label }}</span>
      </div>
    </section>

    <section class="workspace">
      <div class="editor-pane">
        <header class="topbar">
          <div>
            <p class="eyebrow">Source</p>
            <h2>代码编辑器</h2>
          </div>
          <select v-model="selectedExample" class="example-select" @change="loadExample">
            <option v-for="item in examples" :key="item.name" :value="item.name">{{ item.name }}</option>
          </select>
        </header>

        <div class="toolbar" aria-label="compiler actions">
          <button type="button" :disabled="loading" @click="handleAction('lexer')" title="词法分析">
            <ListTree :size="17" />
            <span>词法分析</span>
          </button>
          <button type="button" :disabled="loading" @click="handleAction('parser')" title="语法分析">
            <Braces :size="17" />
            <span>语法分析</span>
          </button>
          <button type="button" :disabled="loading" @click="handleAction('compile')" title="编译">
            <Binary :size="17" />
            <span>编译</span>
          </button>
          <button type="button" class="primary" :disabled="loading" @click="handleAction('run')" title="编译并运行">
            <Play :size="17" />
            <span>编译并运行</span>
          </button>
        </div>

        <CodeEditor v-model="code" />
      </div>

      <aside class="result-pane">
        <div class="panel-header">
          <div>
            <p class="eyebrow">{{ outputStageLabel }}</p>
            <h2>{{ outputTitle }}</h2>
          </div>
          <span class="status" :class="{ error: hasError, busy: loading }">
            <Loader2 v-if="loading" :size="14" class="spin" />
            <AlertTriangle v-else-if="hasError" :size="14" />
            <CheckCircle2 v-else :size="14" />
            {{ statusText }}
          </span>
        </div>

        <div class="result-summary">
          <div>
            <span class="summary-label">阶段</span>
            <strong>{{ outputTitle }}</strong>
          </div>
          <div>
            <span class="summary-label">状态</span>
            <strong>{{ statusText }}</strong>
          </div>
        </div>

        <label class="stdin-label" for="stdin">
          <TerminalSquare :size="15" />
          标准输入
        </label>
        <textarea id="stdin" v-model="stdin" class="stdin" spellcheck="false" placeholder="scanf 后续版本启用" />

        <div class="output-box" :class="{ 'tree-output': outputMode === 'lexer-tree' }">
          <div v-if="outputMode === 'lexer-tree'" class="token-tree">
            <div class="lexer-tree-graph" aria-label="词法分析树">
              <div class="tree-root-row">
                <div class="tree-node root-node">
                  <span class="node-label">Lexer</span>
                  <span class="node-meta">{{ lexerTree.total }} tokens</span>
                </div>
              </div>

              <div class="tree-branches">
                <div v-for="group in lexerTree.groups" :key="group.name" class="tree-branch">
                  <div class="tree-node group-node">
                    <span class="node-label">{{ group.label }}</span>
                    <span class="node-meta">{{ group.tokens.length }}</span>
                  </div>

                  <div class="token-leaves">
                    <div v-for="token in group.tokens" :key="token.key" class="tree-node token-node leaf-node">
                      <span class="token-type">{{ token.type }}</span>
                      <span class="token-lexeme">{{ token.lexeme || 'EOF' }}</span>
                      <span class="node-meta">L{{ token.line }}:C{{ token.column }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="token-table">
              <div class="table-title">Token 明细</div>
              <div class="table-row table-head">
                <span>序号</span>
                <span>类型</span>
                <span>文本</span>
                <span>位置</span>
              </div>
              <div v-for="token in lexerTree.tokens" :key="`row-${token.key}`" class="table-row">
                <span>{{ token.index }}</span>
                <span>{{ token.type }}</span>
                <span>{{ token.lexeme || 'EOF' }}</span>
                <span>{{ token.line }}:{{ token.column }}</span>
              </div>
            </div>
          </div>
          <pre v-else>{{ formattedOutput }}</pre>
        </div>
      </aside>
    </section>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue'
import {
  Activity,
  AlertTriangle,
  Binary,
  Braces,
  CheckCircle2,
  FileCode2,
  ListTree,
  Loader2,
  Play,
  TerminalSquare
} from 'lucide-vue-next'
import CodeEditor from './components/CodeEditor.vue'
import * as compilerApi from './api/compilerApi'

const pipelineStages = [
  { id: 'lexer', index: '01', label: '词法分析' },
  { id: 'parser', index: '02', label: '语法分析' },
  { id: 'semantic', index: '03', label: '语义检查' },
  { id: 'ir', index: '04', label: '中间代码' },
  { id: 'vm', index: '05', label: '虚拟机运行' }
]

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
  }
]

const selectedExample = ref(examples[0].name)
const code = ref(examples[0].code)
const stdin = ref('')
const loading = ref(false)
const outputTitle = ref('运行结果')
const output = ref('点击上方按钮查看 Token、AST、三地址码或程序输出。')
const outputMode = ref('text')
const hasError = ref(false)
const activeStage = ref('vm')
const completedStages = ref([])

const statusText = computed(() => {
  if (loading.value) return '处理中'
  return hasError.value ? '错误' : '就绪'
})

const codeStats = computed(() => ({
  lines: code.value.split('\n').length,
  characters: code.value.length
}))

const outputStageLabel = computed(() => {
  const found = pipelineStages.find(stage => stage.id === activeStage.value)
  return found?.label || 'Output'
})

const formattedOutput = computed(() => {
  if (typeof output.value === 'string') {
    return output.value
  }
  return JSON.stringify(output.value, null, 2)
})

const lexerTree = computed(() => {
  const tokens = Array.isArray(output.value)
    ? output.value.map((token, index) => ({
        ...token,
        index: index + 1,
        key: `${index}-${token.type}-${token.line}-${token.column}-${token.lexeme}`
      }))
    : []

  const groups = [
    { name: 'keywords', label: '关键字 Keywords', matcher: token => token.type.startsWith('KW_') },
    { name: 'identifiers', label: '标识符 Identifiers', matcher: token => token.type === 'IDENTIFIER' },
    { name: 'literals', label: '字面量 Literals', matcher: token => token.type.endsWith('_LITERAL') },
    {
      name: 'operators',
      label: '运算符 Operators',
      matcher: token =>
        [
          'PLUS',
          'MINUS',
          'STAR',
          'SLASH',
          'PERCENT',
          'LT',
          'LTE',
          'GT',
          'GTE',
          'EQEQ',
          'NEQ',
          'ANDAND',
          'OROR',
          'BANG',
          'ASSIGN'
        ].includes(token.type)
    },
    {
      name: 'delimiters',
      label: '分隔符 Delimiters',
      matcher: token =>
        [
          'LPAREN',
          'RPAREN',
          'LBRACE',
          'RBRACE',
          'LBRACKET',
          'RBRACKET',
          'SEMICOLON',
          'COMMA'
        ].includes(token.type)
    },
    { name: 'eof', label: '结束符 EOF', matcher: token => token.type === 'EOF' }
  ].map(group => ({
    ...group,
    tokens: tokens.filter(group.matcher)
  }))

  return {
    total: tokens.length,
    tokens,
    groups: groups.filter(group => group.tokens.length > 0)
  }
})

function loadExample() {
  const found = examples.find(item => item.name === selectedExample.value)
  if (found) {
    code.value = found.code
    hasError.value = false
    activeStage.value = 'vm'
    completedStages.value = []
    outputMode.value = 'text'
    outputTitle.value = '运行结果'
    output.value = '点击上方按钮查看 Token、AST、三地址码或程序输出。'
  }
}

async function handleAction(type) {
  loading.value = true
  hasError.value = false
  outputMode.value = 'text'
  activeStage.value = stageForAction(type)
  completedStages.value = []
  try {
    const result =
      type === 'lexer'
        ? await compilerApi.lex(code.value)
        : type === 'parser'
          ? await compilerApi.parse(code.value)
          : type === 'compile'
            ? await compilerApi.compile(code.value)
            : await compilerApi.run(code.value, stdin.value)

    if (!result.ok) {
      showError(result.payload)
      return
    }

    const payload = result.payload
    if (type === 'lexer') {
      outputTitle.value = '词法分析树'
      outputMode.value = 'lexer-tree'
      output.value = payload.tokens
      completedStages.value = ['lexer']
    } else if (type === 'parser') {
      outputTitle.value = 'AST'
      output.value = payload.ast
      completedStages.value = ['lexer', 'parser']
    } else if (type === 'compile') {
      outputTitle.value = '三地址码'
      output.value = payload.ir.join('\n')
      completedStages.value = ['lexer', 'parser', 'semantic', 'ir']
    } else {
      outputTitle.value = '程序运行结果'
      output.value = `stdout:\n${payload.stdout || '(empty)'}\nstderr:\n${payload.stderr || '(empty)'}\nexitCode: ${payload.exitCode}\n\nIR:\n${payload.ir.join('\n')}`
      completedStages.value = ['lexer', 'parser', 'semantic', 'ir', 'vm']
    }
  } catch (error) {
    showError({
      stage: 'NETWORK',
      message: error.message || '请求失败，请确认后端已启动。',
      line: 0,
      column: 0
    })
  } finally {
    loading.value = false
  }
}

function showError(payload) {
  hasError.value = true
  outputMode.value = 'text'
  outputTitle.value = '错误信息'
  output.value = `stage: ${payload.stage || 'UNKNOWN'}
message: ${payload.message || 'Unknown error'}
line: ${payload.line ?? 0}
column: ${payload.column ?? 0}`
}

function stageForAction(type) {
  if (type === 'lexer') return 'lexer'
  if (type === 'parser') return 'parser'
  if (type === 'compile') return 'ir'
  return 'vm'
}
</script>
