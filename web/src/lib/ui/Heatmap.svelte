<script lang="ts">
  interface Props {
    /** матрица 7×N со значениями 0..1 (строки = недели, колонки = дни недели) */
    weeks: number[][]
    cellSize?: number
  }

  let { weeks, cellSize = 14 }: Props = $props()

  function color(v: number): string {
    if (v <= 0) return 'var(--surface-2)'
    if (v < 0.4) return 'var(--text-muted)'
    if (v < 0.75) return 'var(--text-2)'
    return 'var(--success)'
  }
</script>

<div class="heatmap" role="img" aria-label="Тепловая карта активности">
  {#each weeks as week, wi (wi)}
    <div class="col">
      {#each week as v, di (`${wi}-${di}`)}
        <span class="cell" style="width: {cellSize}px; height: {cellSize}px; background: {color(v)}"></span>
      {/each}
    </div>
  {/each}
</div>

<style>
  .heatmap {
    display: flex;
    gap: 3px;
    overflow-x: auto;
    padding: 4px 0;
  }
  .col {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }
  .cell {
    border-radius: 3px;
    flex-shrink: 0;
  }
</style>
