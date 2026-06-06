<template>
  <div ref="host" class="editor-host" />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as monaco from 'monaco-editor'

const props = defineProps({
  modelValue: { type: String, required: true },
  theme: { type: String, default: 'dark' }
})

const emit = defineEmits(['update:modelValue', 'run'])
const host = ref(null)
let editor
let ignoreNext = false
let themesDefined = false

function defineThemes() {
  if (themesDefined) return
  themesDefined = true

  monaco.editor.defineTheme('cflat-dark', {
    base: 'vs-dark',
    inherit: true,
    rules: [
      { token: 'keyword', foreground: '34d399', fontStyle: 'bold' },
      { token: 'number', foreground: 'ffa657' },
      { token: 'string', foreground: '7ee787' },
      { token: 'comment', foreground: '5d6f64', fontStyle: 'italic' },
      { token: 'operator', foreground: '38bdf8' },
      { token: 'delimiter', foreground: '9aa7b8' },
      { token: 'identifier', foreground: 'e8edf4' }
    ],
    colors: {
      'editor.background': '#0b1018',
      'editor.lineHighlightBackground': '#141b2640',
      'editorLineNumber.foreground': '#3a4654',
      'editorLineNumber.activeForeground': '#9aa7b8',
      'editorCursor.foreground': '#34d399',
      'editor.selectionBackground': '#34d39926',
      'editorIndentGuide.background1': '#1a2230'
    }
  })

  monaco.editor.defineTheme('cflat-light', {
    base: 'vs',
    inherit: true,
    rules: [
      { token: 'keyword', foreground: '0d9b6c', fontStyle: 'bold' },
      { token: 'number', foreground: 'c2410c' },
      { token: 'string', foreground: '15803d' },
      { token: 'comment', foreground: '94a3b8', fontStyle: 'italic' },
      { token: 'operator', foreground: '0ea5e9' },
      { token: 'delimiter', foreground: '64748b' },
      { token: 'identifier', foreground: '0f1c17' }
    ],
    colors: {
      'editor.background': '#ffffff',
      'editor.lineHighlightBackground': '#10b9810a',
      'editorCursor.foreground': '#0d9b6c',
      'editor.selectionBackground': '#10b98124'
    }
  })
}

onMounted(() => {
  monaco.languages.register({ id: 'cflat' })
  monaco.languages.setMonarchTokensProvider('cflat', {
    keywords: [
      'int', 'char', 'bool', 'void', 'if', 'else', 'while', 'for',
      'break', 'continue', 'return', 'true', 'false', 'printf'
    ],
    tokenizer: {
      root: [
        [/[a-zA-Z_][\w_]*/, { cases: { '@keywords': 'keyword', '@default': 'identifier' } }],
        [/\d+/, 'number'],
        [/'(\\.|[^\\'])'/, 'string'],
        [/\/\/.*$/, 'comment'],
        [/\/\*/, 'comment', '@comment'],
        [/[{}()\[\];,]/, 'delimiter'],
        [/[+\-*/%<>=!&|]+/, 'operator']
      ],
      comment: [
        [/[^/*]+/, 'comment'],
        [/\*\//, 'comment', '@pop'],
        [/[/*]/, 'comment']
      ]
    }
  })

  defineThemes()

  editor = monaco.editor.create(host.value, {
    value: props.modelValue,
    language: 'cflat',
    theme: props.theme === 'light' ? 'cflat-light' : 'cflat-dark',
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: 14,
    lineHeight: 22,
    tabSize: 4,
    fontFamily: 'JetBrains Mono, SFMono-Regular, Consolas, monospace',
    fontLigatures: true,
    scrollBeyondLastLine: false,
    smoothScrolling: true,
    cursorBlinking: 'smooth',
    renderLineHighlight: 'all',
    padding: { top: 14, bottom: 14 },
    roundedSelection: true,
    wordWrap: 'on'
  })

  editor.onDidChangeModelContent(() => {
    if (ignoreNext) return
    emit('update:modelValue', editor.getValue())
  })

  // Ctrl/Cmd + Enter to run
  editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => emit('run'))
})

watch(
  () => props.modelValue,
  value => {
    if (editor && value !== editor.getValue()) {
      ignoreNext = true
      editor.setValue(value)
      ignoreNext = false
    }
  }
)

watch(
  () => props.theme,
  t => {
    if (editor) monaco.editor.setTheme(t === 'light' ? 'cflat-light' : 'cflat-dark')
  }
)

onBeforeUnmount(() => editor?.dispose())
</script>
