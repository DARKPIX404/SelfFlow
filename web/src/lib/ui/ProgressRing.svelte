<script lang="ts">
  interface Props {
    value: number
    /** 0..1 */
    size?: number
    stroke?: number
    color?: string
    label?: string
  }

  let { value, size = 96, stroke = 8, color = 'var(--accent)', label }: Props = $props()

  let clamped = $derived(Math.max(0, Math.min(1, value)))
  let r = $derived((size - stroke) / 2)
  let circumference = $derived(2 * Math.PI * r)
  let offset = $derived(circumference * (1 - clamped))
</script>

<div class="ring" style="width: {size}px; height: {size}px">
  <svg width={size} height={size} viewBox="0 0 {size} {size}">
    <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--surface-2)" stroke-width={stroke} />
    <circle
      cx={size / 2}
      cy={size / 2}
      r={r}
      fill="none"
      stroke={color}
      stroke-width={stroke}
      stroke-linecap="round"
      stroke-dasharray={circumference}
      stroke-dashoffset={offset}
      transform="rotate(-90 {size / 2} {size / 2})"
      style="transition: stroke-dashoffset 300ms cubic-bezier(.2,.8,.2,1)"
    />
  </svg>
  {#if label}
    <span class="label">{label}</span>
  {/if}
</div>

<style>
  .ring {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
  .label {
    position: absolute;
    font-size: 18px;
    font-weight: 700;
    color: var(--text);
    font-variant-numeric: tabular-nums;
  }
</style>
