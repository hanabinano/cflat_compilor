<template>
  <main class="app-shell">
    <section class="workspace">
      <div class="editor-pane">
        <header class="topbar">
          <div>
            <p class="eyebrow">Cflat Compiler Lab</p>
            <h1>C♭ 编译器实验台</h1>
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
            <p class="eyebrow">Output</p>
            <h2>{{ outputTitle }}</h2>
          </div>
          <span class="status" :class="{ error: hasError, busy: loading }">{{ statusText }}</span>
        </div>

        <label class="stdin-label" for="stdin">标准输入</label>
        <textarea id="stdin" v-model="stdin" class="stdin" spellcheck="false" placeholder="scanf 后续版本启用" />

        <div class="output-box">
          <pre>{{ formattedOutput }}</pre>
        </div>
      </aside>
    </section>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Binary, Braces, ListTree, Play } from 'lucide-vue-next'
import CodeEditor from './components/CodeEditor.vue'
import * as compilerApi from './api/compilerApi'

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
const hasError = ref(false)

const statusText = computed(() => {
  if (loading.value) return '处理中'
  return hasError.value ? '错误' : '就绪'
})

const formattedOutput = computed(() => {
  if (typeof output.value === 'string') {
    return output.value
  }
  return JSON.stringify(output.value, null, 2)
})

function loadExample() {
  const found = examples.find(item => item.name === selectedExample.value)
  if (found) {
    code.value = found.code
  }
}

async function handleAction(type) {
  loading.value = true
  hasError.value = false
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
      outputTitle.value = 'Token 列表'
      output.value = payload.tokens
    } else if (type === 'parser') {
      outputTitle.value = 'AST'
      output.value = payload.ast
    } else if (type === 'compile') {
      outputTitle.value = '三地址码'
      output.value = payload.ir.join('\n')
    } else {
      outputTitle.value = '程序运行结果'
      output.value = `stdout:\n${payload.stdout || '(empty)'}\nstderr:\n${payload.stderr || '(empty)'}\nexitCode: ${payload.exitCode}\n\nIR:\n${payload.ir.join('\n')}`
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
  outputTitle.value = '错误信息'
  output.value = `stage: ${payload.stage || 'UNKNOWN'}
message: ${payload.message || 'Unknown error'}
line: ${payload.line ?? 0}
column: ${payload.column ?? 0}`
}
</script>
