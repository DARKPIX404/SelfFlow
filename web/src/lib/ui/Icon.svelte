<script lang="ts">
  import { ICON_PATHS, type IconName } from './icons'

  interface Props {
    name: IconName
    size?: number
    strokeWidth?: number
  }

  let { name, size = 22, strokeWidth = 1.8 }: Props = $props()

  let svg = $derived(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${strokeWidth}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON_PATHS[name]}</svg>`,
  )
</script>

{@html svg}
