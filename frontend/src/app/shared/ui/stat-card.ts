import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-stat-card',
  imports: [MatIconModule],
  template: `
    <div class="stat">
      <div class="icon" [style.background]="color() + '1a'" [style.color]="color()">
        <mat-icon>{{ icon() }}</mat-icon>
      </div>
      <div class="text">
        <span class="label">{{ label() }}</span>
        <span class="value">{{ value() }}</span>
        @if (hint()) {
          <span class="hint">{{ hint() }}</span>
        }
      </div>
    </div>
  `,
  styles: `
    .stat {
      display: flex; gap: 14px; align-items: center; height: 100%; box-sizing: border-box;
      padding: 18px; background: var(--mh-surface); border: 1px solid var(--mh-border); border-radius: 16px;
    }
    .icon { display: grid; place-items: center; width: 46px; height: 46px; border-radius: 12px; flex-shrink: 0; }
    .text { display: flex; flex-direction: column; min-width: 0; }
    .label { font-size: 13px; color: var(--mh-text-muted); }
    .value { font-size: 24px; font-weight: 700; line-height: 1.2; }
    .hint { font-size: 12px; color: var(--mh-text-muted); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatCard {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly icon = input('insights');
  readonly color = input('#4f46e5');
  readonly hint = input<string>();
}
