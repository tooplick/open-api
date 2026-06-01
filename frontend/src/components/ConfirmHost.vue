<script setup lang="ts">
import BaseModal from './BaseModal.vue'
import { useConfirmState } from '@/composables/useConfirm'

const { state, accept, cancel } = useConfirmState()
</script>

<template>
  <BaseModal :open="state.open" :title="state.title" width="400px" @close="cancel">
    <p class="confirm-msg">{{ state.message }}</p>
    <template #footer>
      <button class="btn" type="button" @click="cancel">{{ state.cancelText }}</button>
      <button
        :class="['btn', state.danger ? 'btn-danger' : 'btn-primary']"
        type="button"
        @click="accept"
      >
        {{ state.confirmText }}
      </button>
    </template>
  </BaseModal>
</template>

<style scoped>
.confirm-msg {
  color: var(--color-text);
  line-height: 1.6;
  word-break: break-word;
}
</style>
