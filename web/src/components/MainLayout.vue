<script setup lang="ts">
import AppLayout from './AppLayout.vue'
</script>

<template>
  <AppLayout>
    <router-view v-slot="{ Component }">
      <transition name="fade-slide" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </AppLayout>
</template>
