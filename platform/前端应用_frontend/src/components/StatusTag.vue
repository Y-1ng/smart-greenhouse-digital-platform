<script setup lang="ts">
withDefaults(
  defineProps<{
    tone?: 'online' | 'warning' | 'offline'
  }>(),
  {
    tone: 'online',
  },
)
</script>

<template>
  <span
    class="status-tag"
    :class="{
      'status-tag--warning': tone === 'warning',
      'status-tag--offline': tone === 'offline',
    }"
  >
    <slot />
  </span>
</template>

