import { ref, onUnmounted } from 'vue'

export type WsStatus = 'disconnected' | 'connecting' | 'connected'

export function useWebSocket() {
  const status = ref<WsStatus>('disconnected')
  const ws = ref<WebSocket | null>(null)

  let onMessageCallback: ((data: string) => void) | null = null
  let onCloseCallback: (() => void) | null = null

  function connect(url: string) {
    disconnect()
    status.value = 'connecting'

    const socket = new WebSocket(url)
    ws.value = socket

    socket.onopen = () => {
      status.value = 'connected'
    }

    socket.onmessage = (event) => {
      if (onMessageCallback) {
        onMessageCallback(event.data)
      }
    }

    socket.onclose = () => {
      status.value = 'disconnected'
      ws.value = null
      if (onCloseCallback) {
        onCloseCallback()
      }
    }

    socket.onerror = () => {
      status.value = 'disconnected'
    }
  }

  function disconnect() {
    if (ws.value) {
      ws.value.close()
      ws.value = null
    }
    status.value = 'disconnected'
  }

  function send(data: string) {
    if (ws.value && ws.value.readyState === WebSocket.OPEN) {
      ws.value.send(data)
    }
  }

  function onMessage(cb: (data: string) => void) {
    onMessageCallback = cb
  }

  function onClose(cb: () => void) {
    onCloseCallback = cb
  }

  onUnmounted(() => {
    disconnect()
  })

  return { status, connect, disconnect, send, onMessage, onClose }
}
