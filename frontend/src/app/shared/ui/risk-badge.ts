import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';

/** AI return-risk score of a COD order, shown as low / medium / high. Renders nothing without a score. */
@Component({
  selector: 'app-risk-badge',
  imports: [MatTooltipModule],
  template: `
    @if (level(); as l) {
      <span class="risk {{ l }}" [matTooltip]="tooltip()">
        <span class="material-symbols-outlined">{{ l === 'high' ? 'warning' : l === 'medium' ? 'error' : 'verified' }}</span>
        {{ l === 'high' ? 'High' : l === 'medium' ? 'Medium' : 'Low' }} risk · {{ percent() }}%
      </span>
    }
  `,
  styles: `
    .risk { display: inline-flex; align-items: center; gap: 4px; padding: 2px 10px 2px 6px; border-radius: 999px;
      font-size: 12px; font-weight: 600; white-space: nowrap; }
    .risk .material-symbols-outlined { font-size: 16px; }
    .low { background: var(--mh-success-bg); color: var(--mh-success); }
    .medium { background: var(--mh-warning-bg); color: var(--mh-warning); }
    .high { background: var(--mh-danger-bg); color: var(--mh-danger); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RiskBadge {
  readonly score = input<number | null | undefined>();
  protected readonly percent = computed(() => Math.round((this.score() ?? 0) * 100));
  protected readonly level = computed(() => {
    const score = this.score();
    if (score === null || score === undefined) {
      return null;
    }
    return score >= 0.35 ? 'high' : score >= 0.15 ? 'medium' : 'low';
  });
  protected readonly tooltip = computed(
    () => `AI estimate that this cash-on-delivery parcel is refused or returned: ${this.percent()}%`,
  );
}
