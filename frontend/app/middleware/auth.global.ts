import type { Me } from '~/utils/types'

export default defineNuxtRouteMiddleware(async (to) => {
  const me = useMe()
  const checked = useState('sessionChecked', () => false)
  if (!me.value && !checked.value) {
    me.value = await useApi()<Me>('/me').catch(() => null)
    checked.value = true
  }
  if (!me.value && to.path !== '/login') {
    return navigateTo({ path: '/login', query: to.fullPath === '/' ? {} : { next: to.fullPath } })
  }
  if (me.value && to.path === '/login') {
    return navigateTo('/')
  }
})
