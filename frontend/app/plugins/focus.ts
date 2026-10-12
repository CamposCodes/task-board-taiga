export default defineNuxtPlugin((nuxtApp) => {
  nuxtApp.vueApp.directive('focus', {
    mounted: (element: HTMLElement) => element.focus(),
    getSSRProps: () => ({}),
  })
})
