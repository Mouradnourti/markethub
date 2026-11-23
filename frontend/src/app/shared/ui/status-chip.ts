import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { humanize, statusTone } from '../status';

@Component({
  selector: 'app-status-chip',
  template: `<span class="chip tone-{{ tone() }}">{{ label() }}</span>`,
  styles: `
    .chip {
      display: inline-flex;
      align-items: center;
      padding: 2px 10px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 600;
      line-height: 20px;
      white-space: nowrap;
    }
    .tone-neutral { background: var(--mh-surface-2); color: var(--mh-text-muted); }
    .tone-info { background: var(--mh-info-bg); color: var(--mh-info); }
    .tone-progress { background: var(--mh-progress-bg); color: var(--mh-progress); }
    .tone-success { background: var(--mh-success-bg); color: var(--mh-success); }
    .tone-warning { background: var(--mh-warning-bg); color: var(--mh-warning); }
    .tone-danger { background: var(--mh-danger-bg); color: var(--mh-danger); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusChip {
  readonly status = input.required<string>();
  readonly text = input<string>();
  protected readonly tone = computed(() => statusTone(this.status()));
  protected readonly label = computed(() => this.text() ?? humanize(this.status()));
}
