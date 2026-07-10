<template>
  <div class="app-container home">
    <div class="welcome">
      <h1>{{ $t('home.title') }}</h1>
      <p>{{ $t('home.subtitle') }}</p>
    </div>
  </div>
</template>

<script setup name="Index">
</script>

<style scoped lang="scss">
.home {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 84px);
}

.welcome {
  text-align: center;
  color: #666;

  h1 {
    font-size: 28px;
    font-weight: 300;
    margin-bottom: 12px;
    color: #333;
  }

  p {
    font-size: 14px;
  }
}
</style>
