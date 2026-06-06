<template>
  <div class="ast-node">
    <div
      class="ast-row"
      :class="{ clickable: hasChildren }"
      @click="hasChildren && (open = !open)"
    >
      <span class="ast-caret" :class="{ open, leaf: !hasChildren }">
        <ChevronRight :size="13" />
      </span>

      <!-- key (when this node is a named field of its parent) -->
      <span v-if="label" class="ast-key">{{ label }}:</span>

      <!-- object -> show inferred type name -->
      <template v-if="kind === 'object'">
        <span class="ast-type">{{ typeName }}</span>
        <span v-if="!open && summary" class="ast-muted">{{ summary }}</span>
      </template>

      <!-- array -->
      <template v-else-if="kind === 'array'">
        <span class="ast-type">[{{ node.length }}]</span>
        <span v-if="!open" class="ast-muted">…</span>
      </template>

      <!-- primitive -->
      <template v-else>
        <span class="ast-val" :class="{ str: kind === 'string' }">{{ display }}</span>
      </template>
    </div>

    <div v-if="hasChildren && open" class="ast-children">
      <AstTree
        v-for="entry in entries"
        :key="entry.key"
        :node="entry.value"
        :label="entry.label"
        :depth="depth + 1"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ChevronRight } from 'lucide-vue-next'

const props = defineProps({
  node: { default: null },
  label: { type: String, default: '' },
  depth: { type: Number, default: 0 }
})

const open = ref(props.depth < 3)

const kind = computed(() => {
  const n = props.node
  if (n === null || n === undefined) return 'null'
  if (Array.isArray(n)) return 'array'
  if (typeof n === 'object') return 'object'
  if (typeof n === 'string') return 'string'
  return 'primitive'
})

const hasChildren = computed(() => {
  if (kind.value === 'array') return props.node.length > 0
  if (kind.value === 'object') return Object.keys(props.node).length > 0
  return false
})

// Heuristic: infer a readable AST node type from its field signature,
// since the backend serializes Java records without a type tag.
const typeName = computed(() => {
  const n = props.node
  if (kind.value !== 'object') return 'Node'
  const k = Object.keys(n)
  const has = (...f) => f.every(x => k.includes(x))
  if (has('functions', 'globals')) return 'Program'
  if (has('returnType', 'name', 'parameters', 'body')) return 'FunctionDef'
  if (has('type', 'declarators')) return 'VarDecl'
  if (has('name', 'arraySize')) return 'Declarator'
  if (has('type', 'name')) return 'Parameter'
  if (has('left', 'operator', 'right')) return 'Binary'
  if (has('operator', 'expression')) return 'Unary'
  if (has('target', 'value') && k.length === 2) return 'Assign'
  if (has('callee', 'arguments')) return 'Call'
  if (has('array', 'index')) return 'ArrayAccess'
  if (has('value', 'type')) return 'Literal'
  if (has('condition', 'thenBranch')) return 'IfStmt'
  if (has('condition', 'body') && !has('init')) return 'WhileStmt'
  if (has('init', 'condition', 'update')) return 'ForStmt'
  if (has('statements')) return 'Block'
  if (has('expression') && k.length === 1) return 'ExprStmt'
  if (has('value') && k.length === 1) return 'ReturnStmt'
  if (has('name') && k.length === 1) return 'Variable'
  return 'Node'
})

// short inline preview for collapsed objects
const summary = computed(() => {
  const n = props.node
  if (kind.value !== 'object') return ''
  if ('name' in n && typeof n.name === 'string') return n.name
  if ('operator' in n) return n.operator
  if ('value' in n && typeof n.value !== 'object') return String(n.value)
  if ('callee' in n) return n.callee + '()'
  return ''
})

const display = computed(() => {
  if (kind.value === 'null') return 'null'
  if (kind.value === 'string') return `"${props.node}"`
  return String(props.node)
})

const entries = computed(() => {
  if (kind.value === 'array') {
    return props.node.map((value, i) => ({ key: i, label: String(i), value }))
  }
  if (kind.value === 'object') {
    return Object.entries(props.node).map(([label, value]) => ({ key: label, label, value }))
  }
  return []
})
</script>
