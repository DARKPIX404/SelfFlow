<script lang="ts">
  interface Props {
    lines?: number
  }

  let { lines = 3 }: Props = $props()

  const widths = ['100%', '92%', '78%', '85%', '60%']
</script>

<div class="skeleton" aria-hidden="true">
  {#each Array(lines) as _, i (i)}
    <div class="line" style="width: {widths[i % widths.length]}"></div>
  {/each}
</div>

<style>
  .skeleton {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 4px 0;
  }
  .line {
    height: 14px;
    border-radius: 7px;
    background: linear-gradient(90deg, var(--surface) 25%, var(--surface-2) 50%, var(--surface) 75%);
    background-size: 200% 100%;
    animation: shimmer 1.4s infinite;
  }
  @keyframes shimmer {
    from { background-position: 200% 0; }
    to { background-position: -200% 0; }
  }
  @media (prefers-reduced-motion: reduce) {
    .line { animation: none; }
  }
</style>
