import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Shop } from '../../core/models/api.models';

/** Explains why a seller cannot sell yet (pending validation, rejected or suspended shop). */
@Component({
  selector: 'app-shop-status-banner',
  imports: [MatIconModule],
  template: `
    @if (shop(); as s) {
      @if (s.status !== 'APPROVED') {
        <div class="banner {{ s.status.toLowerCase() }}">
          <mat-icon>{{ s.status === 'PENDING' ? 'hourglass_top' : 'block' }}</mat-icon>
          <div>
            <strong>
              @switch (s.status) {
                @case ('PENDING') { Your shop "{{ s.name }}" is awaiting validation }
                @case ('REJECTED') { Your shop application was rejected }
                @default { Your shop is suspended }
              }
            </strong>
            <span>
              @switch (s.status) {
                @case ('PENDING') { An administrator reviews new shops. You will be able to add products and receive orders once approved. }
                @default { Contact the MarketHub team to find out more. }
              }
            </span>
          </div>
        </div>
      }
    }
  `,
  styles: `
    .banner { display: flex; gap: 14px; align-items: center; padding: 16px 18px; border-radius: 16px; margin-bottom: 20px; }
    .banner div { display: flex; flex-direction: column; gap: 2px; }
    .banner span { font-size: 14px; opacity: 0.9; }
    .pending { background: var(--mh-warning-bg); color: var(--mh-warning); }
    .rejected, .suspended { background: var(--mh-danger-bg); color: var(--mh-danger); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShopStatusBanner {
  readonly shop = input<Shop | null>(null);
}
