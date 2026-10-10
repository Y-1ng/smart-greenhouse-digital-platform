<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheck, Clock3, FlaskConical, LoaderCircle } from 'lucide-vue-next'

type FreshnessMode = 'real' | 'demo' | 'stale' | 'loading'

const props = withDefaults(
  defineProps<{
    mode: FreshnessMode
    label: string
  }>(),
  {
    mode: 'demo',
  },
)

const icon = computed(() => {
  if (props.mode === 'real') return CircleCheck
  if (props.mode === 'stale') return Clock3
  if (props.mode === 'loading') return LoaderCircle
  return FlaskConical
})
</script>

<template>
  <span
    class="data-freshness"
    :class="`is-${mode}`"
    role="status"
    aria-live="polite"
  >
    <component :is="icon" :size="13" />
    {{ label }}
  </span>
</template>

<style scoped>
.data-freshness {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  padding: 0 9px;
  color: #52645a;
  background: #f3f6f4;
  border: 1px solid #dce5df;
  border-radius: 999px;
  font-size: 10px;
  white-space: nowrap;
}

.data-freshness.is-real {
  color: #267a58;
  background: #e8f4ee;
  border-color: #c3dfcf;
}

.data-freshness.is-demo {
  color: #62736a;
}

.data-freshness.is-stale {
  color: #9d6918;
  background: #fbf2df;
  border-color: #ead19a;
}

.data-freshness.is-loading {
  color: #52645a;
}

.data-freshness.is-loading svg {
  animation: freshness-spin 900ms linear infinite;
}

@keyframes freshness-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
