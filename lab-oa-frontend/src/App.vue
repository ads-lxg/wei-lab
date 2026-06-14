<script setup lang="ts">
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
appStore.initTheme()
</script>

<template>
  <router-view />
</template>
