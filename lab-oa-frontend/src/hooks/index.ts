import { onMounted, onUnmounted, ref, type Ref } from 'vue'

/** Keyboard shortcut handler */
export function useKeydown(map: Record<string, () => void>) {
  function handler(e: KeyboardEvent) {
    const key = [
      e.ctrlKey ? 'Ctrl' : '',
      e.metaKey ? 'Meta' : '',
      e.shiftKey ? 'Shift' : '',
      e.altKey ? 'Alt' : '',
      e.key,
    ].filter(Boolean).join('+')
    map[key]?.()
  }
  onMounted(() => window.addEventListener('keydown', handler))
  onUnmounted(() => window.removeEventListener('keydown', handler))
}

/** Simple copy to clipboard */
export function useClipboard(): { copy: (text: string) => Promise<void>; copied: Ref<boolean> } {
  const copied = ref(false)
  async function copy(text: string) {
    await navigator.clipboard.writeText(text)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  }
  return { copy, copied }
}

/** Debounced ref */
export function useDebouncedRef<T>(value: T, delay: number = 300): Ref<T> {
  let timeout: ReturnType<typeof setTimeout>
  const debounced = ref(value) as Ref<T>

  return new Proxy(debounced, {
    set(target, p, newValue) {
      clearTimeout(timeout)
      timeout = setTimeout(() => {
        (target as Record<PropertyKey, unknown>)[p as string] = newValue
      }, delay)
      return true
    },
  })
}
