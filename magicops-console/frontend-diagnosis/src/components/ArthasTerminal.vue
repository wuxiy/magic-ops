<template>
  <div ref="terminalEl" class="arthas-terminal" />
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { WebLinksAddon } from '@xterm/addon-web-links'
import '@xterm/xterm/css/xterm.css'
import { useWebSocket, type WsStatus } from '@/composables/useWebSocket'

defineProps<{
  sessionId: string | null
}>()

const terminalEl = ref<HTMLDivElement | null>(null)
const wsStatus = ref<WsStatus>('disconnected')

let terminal: Terminal | null = null
let fitAddon: FitAddon | null = null

const { status, connect, disconnect, send, onMessage, onClose } = useWebSocket()

watch(status, (val) => {
  wsStatus.value = val
})

function initTerminal() {
  if (!terminalEl.value) return

  terminal = new Terminal({
    cursorBlink: true,
    fontSize: 14,
    fontFamily: '"JetBrains Mono", "Fira Code", "Cascadia Code", Menlo, monospace',
    theme: {
      background: '#0a0a0a',
      foreground: '#e0e0e0',
      cursor: '#4af626',
      selectionBackground: '#264f78',
      black: '#000000',
      red: '#ff6b6b',
      green: '#4af626',
      yellow: '#ffd93d',
      blue: '#6bc5ff',
      magenta: '#c678dd',
      cyan: '#56d6c2',
      white: '#e0e0e0',
    },
    allowTransparency: false,
    scrollback: 5000,
  })

  fitAddon = new FitAddon()
  terminal.loadAddon(fitAddon)
  terminal.loadAddon(new WebLinksAddon())

  terminal.open(terminalEl.value)
  fitAddon.fit()

  terminal.writeln('\x1b[1;32m=== MagicOps Arthas 诊断终端 ===\x1b[0m')
  terminal.writeln('\x1b[90m等待会话连接...\x1b[0m')
  terminal.writeln('')

  terminal.onData((data) => {
    send(data)
  })

  window.addEventListener('resize', handleResize)
}

function handleResize() {
  if (fitAddon) {
    fitAddon.fit()
  }
}

function connectToSession(sessionId: string) {
  disconnect()

  if (!terminal) return

  terminal.clear()
  terminal.writeln('\x1b[1;33m正在连接会话 ' + sessionId + ' ...\x1b[0m')
  terminal.writeln('')

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/api/diagnosis/ws/sessions/${sessionId}`

  onMessage((data: string) => {
    if (terminal) {
      terminal.write(data)
    }
  })

  onClose(() => {
    if (terminal) {
      terminal.writeln('')
      terminal.writeln('\x1b[1;31m[连接已断开]\x1b[0m')
    }
  })

  connect(wsUrl)
}

onMounted(() => {
  initTerminal()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  disconnect()
  if (terminal) {
    terminal.dispose()
    terminal = null
  }
})

defineExpose({ connectToSession, wsStatus })
</script>

<style scoped>
.arthas-terminal {
  width: 100%;
  height: 100%;
  padding: 4px;
  box-sizing: border-box;
  background: #0a0a0a;
}

.arthas-terminal :deep(.xterm) {
  height: 100%;
}

.arthas-terminal :deep(.xterm-viewport) {
  overflow-y: auto !important;
}
</style>
