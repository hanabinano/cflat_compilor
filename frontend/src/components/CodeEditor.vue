<template>
  <div ref="host" class="editor-host" />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as monaco from 'monaco-editor'

const props = defineProps({
  modelValue: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['update:modelValue'])
const host = ref(null)
let editor
let ignoreNext = false

onMounted(() => {
  monaco.languages.register({ id: 'cflat' })
  monaco.languages.setMonarchTokensProvider('cflat', {
    keywords: [
      'int',
      'char',
      'bool',
      'void',
      'if',
      'else',
      'while',
      'for',
      'break',
      'continue',
      'return',
      'true',
      'false',
      'printf'
    ],
    tokenizer: {
      root: [
        [/[a-zA-Z_][\w_]*/, { cases: { '@keywords': 'keyword', '@default': 'identifier' } }],
        [/[0-9]+/, 'number'],
        [/'(\\.|[^\\'])'/, 'string'],
        [/\/\/.*$/, 'comment'],
        [/\/\*/, 'comment', '@comment'],
        [/[{}()[\];,]/, 'delimiter'],
        [/[+\-*/%<>=!&|]+/, 'operator']
      ],
      comment: [
        [/[^/*]+/, 'comment'],
        [/\*\//, 'comment', '@pop'],
        [/[/*]/, 'comment']
      ]
    }
  })

  editor = monaco.editor.create(host.value, {
    value: props.modelValue,
    language: 'cflat',
    theme: 'vs',
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: 14,
    lineHeight: 22,
    tabSize: 4,
    scrollBeyondLastLine: false,
    wordWrap: 'on'
  })

  editor.onDidChangeModelContent(() => {
    if (ignoreNext) {
      return
    }
    emit('update:modelValue', editor.getValue())
  })
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

onBeforeUnmount(() => {
  editor?.dispose()
})
</script>
