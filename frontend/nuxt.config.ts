export default defineNuxtConfig({
  compatibilityDate: '2026-10-01',
  modules: ['nuxt-quasar-ui'],
  devtools: { enabled: false },
  css: [
    '@fontsource/ubuntu/400.css',
    '@fontsource/ubuntu/500.css',
    '@fontsource/ubuntu/700.css',
    '~/assets/app.css',
  ],

  runtimeConfig: {
    apiInternal: 'http://localhost:8080',
  },

  nitro: {
    devProxy: {
      '/api': { target: 'http://localhost:8080/api', changeOrigin: true },
    },
  },

  app: {
    head: {
      title: 'Taskboard',
      htmlAttrs: { lang: 'pt-BR' },
      meta: [{ name: 'viewport', content: 'width=device-width, initial-scale=1' }],
      link: [{ rel: 'icon', type: 'image/svg+xml', href: '/r2da-mark.svg' }],
    },
  },

  quasar: {
    plugins: ['Notify', 'Dialog'],
    lang: 'pt-BR',
    iconSet: 'svg-mdi-v7',
    extras: {},
    config: {
      brand: {
        primary: '#006557',
        secondary: '#8de1c6',
        accent: '#10948b',
        positive: '#207c1e',
        negative: '#c92e2e',
        warning: '#a88d00',
        info: '#006557',
      },
      notify: { position: 'bottom-right', timeout: 4000 },
    },
  },
})
