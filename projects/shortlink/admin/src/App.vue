<template>
  <!-- 根组件：交给 router-view 渲染当前路由 -->
  <router-view />
</template>

<script setup>
// App.vue 是个空壳，所有布局交给 Layout 组件 + 各 view 完成。
// 这样切换页面时整个 Layout 不会跟着重建，性能更好。
</script>

<style>
/* App 级别样式留空，具体样式在 style.css */
</style>