import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { OrderApi } from '../../core/api/order.api';
import { Order, ShopOrder } from '../../core/models/api.models';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { NotificationService } from '../../core/notifications/notification.service';
import { Notifier } from '../../core/ui/notifier.service';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { humanize, paymentLabel } from '../../shared/status';
import { StatusChip } from '../../shared/ui/status-chip';

const TRACK: ShopOrder['status'][] = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED'];

@Component({
  selector: 'app-order-detail-page',
  imports: [
    RouterLink,
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MoneyPipe,
    StatusChip,
    TranslatePipe,
  ],
  templateUrl: './order-detail.page.html',
  styleUrl: './order-detail.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderDetailPage {
  readonly id = input.required<string>();
  readonly placed = input<string>();
  private readonly api = inject(OrderApi);
  private readonly notifier = inject(Notifier);

  protected readonly order = signal<Order | null>(null);
  protected readonly returnFor = signal<number | null>(null);
  protected readonly busy = signal(false);
  /** Set when a parcel status arrived through the live stream while the page was open. */
  protected readonly liveUpdated = signal(false);
  protected returnReason = '';
  protected readonly track = TRACK;
  protected readonly humanize = humanize;
  protected readonly paymentLabel = paymentLabel;

  protected readonly cancellable = computed(() =>
    (this.order()?.shopOrders ?? []).every((so) => so.status === 'PENDING' || so.status === 'AWAITING_PAYMENT'),
  );

  constructor() {
    effect(() => this.load(Number(this.id())));
    inject(NotificationService)
      .incoming.pipe(takeUntilDestroyed())
      .subscribe((n) => {
        if (n.link === `/orders/${this.id()}`) {
          this.liveUpdated.set(true);
          this.load(Number(this.id()));
        }
      });
  }

  protected stepReached(parcel: ShopOrder, step: ShopOrder['status']): boolean {
    return parcel.history.some((h) => h.status === step);
  }

  protected cancel(): void {
    const order = this.order();
    if (!order || !confirm('Cancel this order? Reserved items will be released.')) {
      return;
    }
    this.busy.set(true);
    this.api.cancel(order.id, 'Cancelled from my account').subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.busy.set(false);
        this.notifier.success('Your order was cancelled.');
      },
      error: () => this.busy.set(false),
    });
  }

  protected submitReturn(parcel: ShopOrder): void {
    if (this.returnReason.trim().length < 5) {
      this.notifier.error('Please describe the problem (at least 5 characters).');
      return;
    }
    this.busy.set(true);
    this.api.requestReturn(parcel.id, 'RETURN', this.returnReason.trim()).subscribe({
      next: () => {
        this.notifier.success('Return requested. Our team will review it shortly.');
        this.returnFor.set(null);
        this.returnReason = '';
        this.busy.set(false);
        this.load(this.order()!.id);
      },
      error: () => this.busy.set(false),
    });
  }

  protected downloadInvoice(): void {
    const order = this.order();
    if (!order) {
      return;
    }
    this.api.invoice(order.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `invoice-${order.reference}.pdf`;
      link.click();
      URL.revokeObjectURL(url);
    });
  }

  private load(id: number): void {
    this.api.get(id).subscribe((order) => this.order.set(order));
  }
}
