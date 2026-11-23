import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { RouterLink } from '@angular/router';
import { OrderApi } from '../../core/api/order.api';
import { Order, Page } from '../../core/models/api.models';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { StatusChip } from '../../shared/ui/status-chip';

@Component({
  selector: 'app-orders-page',
  imports: [RouterLink, DatePipe, MatButtonModule, MatIconModule, MatPaginatorModule, MoneyPipe, StatusChip, EmptyState],
  template: `
    <div class="container page">
      <div class="page-title"><div><h1>My orders</h1><p>Track your parcels, request returns and download invoices.</p></div></div>
      @if (orders(); as page) {
        @for (order of page.content; track order.id) {
          <a class="panel order" [routerLink]="['/orders', order.id]">
            <div class="head">
              <div>
                <strong>{{ order.reference }}</strong>
                <span class="muted">{{ order.createdAt | date: 'medium' }}</span>
              </div>
              <div class="right">
                <app-status-chip [status]="order.paymentStatus" [text]="order.paymentMethod === 'COD' ? 'COD · ' + order.paymentStatus.toLowerCase() : 'Card · ' + order.paymentStatus.toLowerCase()" />
                <strong class="total">{{ order.total | money }}</strong>
              </div>
            </div>
            <div class="parcels">
              @for (parcel of order.shopOrders; track parcel.id) {
                <div class="parcel">
                  <mat-icon>inventory_2</mat-icon>
                  <span class="shop">{{ parcel.shopName }}</span>
                  <span class="muted">{{ parcel.items.length }} item(s)</span>
                  <span class="spacer"></span>
                  <app-status-chip [status]="parcel.status" />
                </div>
              }
            </div>
          </a>
        } @empty {
          <div class="panel">
            <app-empty-state icon="receipt_long" title="No orders yet" message="Your orders will appear here.">
              <a mat-flat-button routerLink="/products">Start shopping</a>
            </app-empty-state>
          </div>
        }
        @if (page.totalPages > 1) {
          <mat-paginator [length]="page.totalElements" [pageIndex]="page.page" [pageSize]="page.size"
                         [hidePageSize]="true" (page)="load($event.pageIndex)" />
        }
      }
    </div>
  `,
  styles: `
    .order { display: block; margin-bottom: 14px; color: inherit; text-decoration: none; transition: border-color .15s; }
    .order:hover { border-color: var(--mh-accent); }
    .head { display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; align-items: center; }
    .head > div:first-child { display: flex; flex-direction: column; }
    .right { display: flex; gap: 14px; align-items: center; }
    .total { font-size: 18px; }
    .parcels { margin-top: 12px; border-top: 1px solid var(--mh-border); }
    .parcel { display: flex; gap: 10px; align-items: center; padding: 10px 0; }
    .parcel + .parcel { border-top: 1px dashed var(--mh-border); }
    .shop { font-weight: 600; }
    mat-paginator { background: transparent; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrdersPage {
  private readonly api = inject(OrderApi);
  protected readonly orders = signal<Page<Order> | null>(null);

  constructor() {
    this.load(0);
  }

  protected load(page: number): void {
    this.api.list(page, 10).subscribe((result) => this.orders.set(result));
  }
}
