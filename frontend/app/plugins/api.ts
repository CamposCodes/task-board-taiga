import { Notify } from 'quasar'

export default defineNuxtPlugin((nuxtApp) => {
  const config = useRuntimeConfig()
  const cookie = import.meta.server ? useRequestHeaders(['cookie']).cookie : undefined

  const api = $fetch.create({
    baseURL: (import.meta.server ? config.apiInternal : '') + '/api/v1',
    headers: { 'X-Requested-With': 'taskboard', ...(cookie ? { cookie } : {}) },
    async onResponseError({ request, response, options }) {
      const url = String(request)
      const isSessionProbe = url.endsWith('/me')
      const quiet = (options as { quiet?: boolean }).quiet === true
      if (response.status === 401 && !isSessionProbe && !url.endsWith('/auth/login')) {
        useState('me').value = null
        await nuxtApp.runWithContext(() => navigateTo('/login'))
      } else if (import.meta.client && !isSessionProbe && !quiet) {
        Notify.create({ type: 'negative', message: response._data?.detail || `Erro ${response.status}` })
      }
    },
  })

  if (import.meta.client) {
    let startupHandler = nuxtApp.vueApp.config.errorHandler
    nuxtApp.hook('app:suspense:resolve', () => {
      startupHandler = undefined
    })
    nuxtApp.vueApp.config.errorHandler = (error, instance, info) => {
      if ((error as Error)?.name === 'FetchError') {
        return
      }
      if (startupHandler) {
        startupHandler(error, instance, info)
      } else {
        console.error(error)
      }
    }
  }

  return { provide: { api } }
})
