<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    label: string
    placeholder?: string
    minHeight?: number
    autofocus?: boolean
    actions?: boolean
    saveLabel?: string
    compact?: boolean
  }>(),
  { placeholder: '', minHeight: 128, actions: true, saveLabel: 'Salvar' },
)
const text = defineModel<string>({ required: true })
const emit = defineEmits<{ save: []; cancel: [] }>()

const area = ref<HTMLTextAreaElement>()
const preview = ref(false)
const open = ref(!props.compact)
const block = ref('')
const BLOCK = /^(#{1,6} |> |[-*] \[[ xX]\] |[-*] |\d+\. )/

onMounted(() => props.autofocus && area.value?.focus())
watch(text, (value) => {
  if (props.compact && !value && document.activeElement !== area.value) {
    open.value = false
    preview.value = false
  }
})

function selection() {
  const el = area.value!
  return { start: el.selectionStart, end: el.selectionEnd, value: el.value }
}

function lines() {
  const { start, end, value } = selection()
  const from = value.lastIndexOf('\n', start - 1) + 1
  const stop = value.indexOf('\n', end)
  const to = stop === -1 ? value.length : stop
  return { from, to, rows: value.slice(from, to).split('\n') }
}

function put(start: number, end: number, value: string, select: [number, number]) {
  const el = area.value!
  el.focus()
  el.setSelectionRange(start, end)
  if ((value || start !== end) && !document.execCommand(value ? 'insertText' : 'delete', false, value)) {
    el.setRangeText(value, start, end)
    el.dispatchEvent(new Event('input', { bubbles: true }))
  }
  el.setSelectionRange(start + select[0], start + select[1])
  cursor()
}

function wrap(mark: string, sample = 'texto') {
  const { start, end, value } = selection()
  const n = mark.length
  const stars = /\**$/.exec(value.slice(0, start))![0].length
  const wrapped =
    mark === '*' ? stars % 2 === 1 && value[end] === '*' : value.slice(start - n, start) === mark && value.slice(end, end + n) === mark
  if (wrapped) {
    return put(start - n, end + n, value.slice(start, end), [0, end - start])
  }
  const inner = value.slice(start, end) || sample
  put(start, end, mark + inner + mark, [n, n + inner.length])
}

function prefix(make: (index: number) => string) {
  const { from, to, rows } = lines()
  const kind = (row: string) => (BLOCK.exec(row)?.[0] ?? '').replace(/\[[xX]\]/, '[ ]')
  const filled = (row: string) => rows.length === 1 || row.trim() !== ''
  const same = rows.every((row, index) => !filled(row) || kind(row) === make(index))
  const next = rows.map((row, index) => (filled(row) ? (same ? '' : make(index)) + row.replace(BLOCK, '') : row)).join('\n')
  put(from, to, next, rows.length === 1 ? [next.length, next.length] : [0, next.length])
}

function fence(before: string, sample: string, after: string) {
  const { start, end, value } = selection()
  const inner = value.slice(start, end) || sample
  const head = start === 0 || value.slice(0, start).endsWith('\n\n') ? '' : value[start - 1] === '\n' ? '\n' : '\n\n'
  const tail = end === value.length || value.startsWith('\n\n', end) ? '' : value[end] === '\n' ? '\n' : '\n\n'
  const at = head.length + before.length
  put(start, end, head + before + inner + after + tail, [at, at + inner.length])
}

function link(image = false) {
  const { start, end, value } = selection()
  const selected = value.slice(start, end)
  const isUrl = /^(https?:\/\/|www\.)\S+$/.test(selected)
  const label = isUrl || !selected ? (image ? 'descrição' : 'texto') : selected
  const url = isUrl ? selected : 'https://'
  const lead = image ? '![' : '['
  const at = isUrl || !selected ? lead.length : lead.length + label.length + 2
  put(start, end, `${lead}${label}](${url})`, [at, at + (isUrl || !selected ? label.length : url.length)])
}

function align(side: 'left' | 'center' | 'right') {
  const { start, end, value } = selection()
  for (const match of value.matchAll(/<div align="(?:center|right)">\n\n([\s\S]*?)\n\n<\/div>/g)) {
    if (match.index <= start && end <= match.index + match[0].length) {
      const next = side === 'left' ? match[1]! : `<div align="${side}">\n\n${match[1]}\n\n</div>`
      return put(match.index, match.index + match[0].length, next, [0, next.length])
    }
  }
  if (side !== 'left') {
    fence(`<div align="${side}">\n\n`, 'texto', '\n\n</div>')
  }
}

function rule() {
  const { end } = selection()
  area.value!.setSelectionRange(end, end)
  fence('---', '', '')
}

function clear() {
  const { start, end, value } = selection()
  const { from, to } = lines()
  const [a, b] = start === end ? [from, to] : [start, end]
  const emphasis = (source: string) => source.replace(/(\*\*\*|\*\*|\*|~~|`)(.+?)\1/g, '$2')
  const plain = emphasis(emphasis(value.slice(a, b).replace(/<\/?div[^>]*>\n*/g, '').replace(/!?\[([^\]]*)\]\([^)]*\)/g, '$1')))
    .replace(new RegExp(BLOCK.source, 'gm'), '')
  put(a, b, plain, [0, plain.length])
}

function history(command: 'undo' | 'redo') {
  area.value!.focus()
  document.execCommand(command)
}

function cursor() {
  const el = area.value
  if (el) {
    const from = el.value.lastIndexOf('\n', el.selectionStart - 1) + 1
    block.value = String(/^(#{1,3}) /.exec(el.value.slice(from))?.[1]!.length ?? '')
  }
}

const bold = () => wrap('**')
const italic = () => wrap('*')
const strike = () => wrap('~~')

function onKey(event: KeyboardEvent) {
  const key = (event.ctrlKey || event.metaKey) && !event.altKey ? event.key.toLowerCase() : ''
  const run = event.shiftKey ? { x: strike }[key] : { b: bold, i: italic, k: () => link() }[key]
  if (run) {
    event.preventDefault()
    run()
  }
}

function keepFocus(event: MouseEvent) {
  if (!(event.target as HTMLElement).closest('select')) {
    event.preventDefault()
  }
}

function cancel() {
  preview.value = false
  open.value = !props.compact
  emit('cancel')
}

const TABLE = ' | Coluna 2 | Coluna 3 |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |'
const tools = [
  [
    { icon: icons.bold, title: 'Negrito (Ctrl+B)', run: bold },
    { icon: icons.italic, title: 'Itálico (Ctrl+I)', run: italic },
    { icon: icons.strike, title: 'Tachado (Ctrl+Shift+X)', run: strike },
    { icon: icons.link, title: 'Link (Ctrl+K)', run: () => link() },
    { icon: icons.taskList, title: 'Lista de tarefas', run: () => prefix(() => '- [ ] ') },
    { icon: icons.bullets, title: 'Lista com marcadores', run: () => prefix(() => '- ') },
    { icon: icons.numbers, title: 'Lista numerada', run: () => prefix((index) => `${index + 1}. `) },
    { icon: icons.alignLeft, title: 'Alinhar à esquerda', run: () => align('left') },
    { icon: icons.alignCenter, title: 'Centralizar', run: () => align('center') },
    { icon: icons.alignRight, title: 'Alinhar à direita', run: () => align('right') },
  ],
  [
    { icon: icons.image, title: 'Imagem por endereço', run: () => link(true) },
    { icon: icons.quote, title: 'Citação', run: () => prefix(() => '> ') },
    { icon: icons.table, title: 'Tabela', run: () => fence('| ', 'Coluna 1', TABLE) },
    { icon: icons.codeBlock, title: 'Bloco de código', run: () => fence('```\n', 'código', '\n```') },
    { icon: icons.clearFormat, title: 'Remover formatação', run: clear },
    { icon: icons.rule, title: 'Linha horizontal', run: rule },
    { icon: icons.undo, title: 'Desfazer (Ctrl+Z)', run: () => history('undo') },
    { icon: icons.redo, title: 'Refazer (Ctrl+Y)', run: () => history('redo') },
  ],
]
</script>

<template>
  <div class="md-editor">
    <div class="md-box">
      <div v-if="open" class="md-bar" role="toolbar" :aria-label="`Formatação: ${label}`" @mousedown="keepFocus">
        <select v-model="block" class="md-block" aria-label="Estilo do parágrafo" :disabled="preview" @change="prefix(() => (block ? '#'.repeat(Number(block)) + ' ' : ''))">
          <option value="">Parágrafo</option>
          <option value="1">Título 1</option>
          <option value="2">Título 2</option>
          <option value="3">Título 3</option>
        </select>
        <template v-for="(group, index) in tools" :key="index">
          <span class="md-sep" />
          <button v-for="tool in group" :key="tool.title" class="md-tool" type="button" :title="tool.title" :aria-label="tool.title" :disabled="preview" @click="tool.run">
            <q-icon :name="tool.icon" size="20px" />
          </button>
        </template>
      </div>
      <ClientOnly v-if="preview">
        <MarkdownView class="md-preview" :source="text" :style="{ minHeight: `${minHeight}px` }" />
      </ClientOnly>
      <textarea
        v-else
        ref="area"
        v-model="text"
        :aria-label="label"
        :placeholder="placeholder"
        :style="{ minHeight: `${open ? minHeight : 40}px` }"
        @focus="open = true"
        @keydown="onKey"
        @keyup="cursor"
        @click="cursor"
      />
      <div v-if="open && actions" class="md-actions">
        <button class="btn" type="button" @click="emit('save')">{{ saveLabel }}</button>
        <button class="btn gray" type="button" @click="cancel">Cancelar</button>
      </div>
    </div>
    <div v-if="open" class="md-foot">
      <button class="md-mode" type="button" :aria-pressed="preview" @click="preview = !preview">{{ preview ? 'Editar' : 'Visualizar' }}</button>
      <span class="muted"><slot name="hint">Markdown</slot></span>
    </div>
  </div>
</template>
