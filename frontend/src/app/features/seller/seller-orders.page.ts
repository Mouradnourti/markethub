import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { SellerApi } from '../../core/api/seller.api';
import { OrderStatus, Page, SellerOrder } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { SELLER_NEXT_STATUSES, humanize } from '../../shared/status';
import { EmptyState } from '../../shared/ui/empty-state';
import { RiskBadge } from '../../shared/ui/risk-badge';
import { StatusChip } from '../../shared/ui/status-chip';

const ACTION_LABELS: Partial<Record<OrderStatus, string>> = {
  CONFIRMED: 'Confirm',
  SHIPPED: 'Mark shipped',
  DELIVERED: 'Mark delivered',
  RETURNED: 'Refused / returned',
  CANCELLED: 'Cancel',
};

@Component({
  selector: 'app-seller-orders-page',
  imports: [
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MoneyPipe,
    StatusChip,
    RiskBadge,
    EmptyState,
  ],
  templateUrl: './seller-orders.page.html',
  styleUrl: './seller-orders.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SellerOrdersPage {
  private readonly api = inject(SellerApi);
  private readonly notifier = inject(Notifier);
  protected readonly filter = signal<OrderStatus | null>('PENDING');
  protected readonly page = signal<Page<SellerOrder> | null>(null);
  protected readonly selected = signal<SellerOrder | null>(null);
  protected readonly busy = signal(false);
  protected trackingNumber = '';
  protected note = '';
  protected readonly filters: (OrderStatus | null)[] = [null, 'PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'RETURNED', 'CANCELLED'];
  protected readonly humanize = humanize;

  constructor() {
    this.load(0);
  }

  protected setFilter(status: OrderStatus | null): void {
    this.filter.set(status);
    this.selected.set(null);
    this.load(0);
  }

  protected load(pageIndex: number): void {
    this.api.orders(this.filter(), pageIndex, 12).subscribe((page) => this.page.set(page));
  }

  protected select(order: SellerOrder): void {
    this.selected.set(order);
    this.trackingNumber = order.trackingNumber ?? '';
    this.note = '';
  }

  protected nextStatuses(order: SellerOrder): OrderStatus[] {
    return SELLER_NEXT_STATUSES[order.status] ?? [];
  }

  protected actionLabel(status: OrderStatus): string {
    return ACTION_LABELS[status] ?? humanize(status);
  }

  protected move(order: SellerOrder, status: OrderStatus): void {
    if ((status === 'CANCELLED' || status === 'RETURNED') && !confirm(`${this.actionLabel(status)} order ${order.orderReference}? Stock will be released.`)) {
      return;
    }
    this.busy.set(true);
    this.api.updateOrderStatus(order.id, status, this.trackingNumber || undefined, this.note || undefined).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.selected.set(updated);
        this.notifier.success(`Order ${updated.orderReference} is now ${humanize(updated.status).toLowerCase()}`);
        this.load(this.page()?.page ?? 0);
      },
      error: () => this.busy.set(false),
    });
  }
}
