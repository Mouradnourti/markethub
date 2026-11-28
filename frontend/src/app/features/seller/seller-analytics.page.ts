import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { ChartConfiguration } from 'chart.js';
import { SellerApi } from '../../core/api/seller.api';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ProductPerformance, SellerAnalytics } from '../../core/models/api.models';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { ChartComponent } from '../../shared/ui/chart';

type SortKey = 'revenue' | 'views' | 'conversionRate' | 'returnRate';

/** Conversion analytics: page views against orders, per day and per product, with returns. */
@Component({
  selector: 'app-seller-analytics-page',
  imports: [DecimalPipe, RouterLink, MatButtonToggleModule, MatIconModule, MatProgressBarModule, ChartComponent, MoneyPipe,
    TranslatePipe],
  template: `
    <div class="page-title">
      <div>
        <h1>{{ 'Analytics' | t }}</h1>
        <p>{{ 'How shoppers find and buy your products. A conversion is an order containing the product.' | t }}</p>
      </div>
      <mat-button-toggle-group [value]="days()" (change)="load($event.value)" [attr.aria-label]="'Period' | t">
        <mat-button-toggle [value]="7">{{ '7 days' | t }}</mat-button-toggle>
        <mat-button-toggle [value]="30">{{ '30 days' | t }}</mat-button-toggle>
        <mat-button-toggle [value]="90">{{ '90 days' | t }}</mat-button-toggle>
      </mat-button-toggle-group>
    </div>

    @if (loading()) { <mat-progress-bar mode="indeterminate" /> }

    @if (data(); as d) {
      <div class="kpis">
        <div class="kpi"><span>{{ 'Product page views' | t }}</span><strong>{{ d.views | number }}</strong></div>
        <div class="kpi"><span>{{ 'Orders' | t }}</span><strong>{{ d.orders | number }}</strong></div>
        <div class="kpi accent"><span>{{ 'Conversion rate' | t }}</span><strong>{{ d.conversionRate | number: '1.1-1' }}%</strong></div>
        <div class="kpi"><span>{{ 'Revenue' | t }}</span><strong>{{ d.revenue | money }}</strong></div>
        <div class="kpi"><span>{{ 'Average order' | t }}</span><strong>{{ d.averageOrderValue | money }}</strong></div>
        <div class="kpi"><span>{{ 'Return rate' | t }}</span><strong>{{ d.returnRate | number: '1.1-1' }}%</strong></div>
        <div class="kpi"><span>{{ 'Wishlist saves' | t }}</span><strong>{{ d.wishlistSaves | number }}</strong></div>
      </div>

      <section class="panel chart-panel">
        <h2>{{ 'Views and orders per day' | t }}</h2>
        <app-chart [config]="chart()" [height]="300" />
      </section>

      <section class="panel">
        <div class="table-head">
          <h2>{{ 'Products' | t }}</h2>
          <span class="muted small">{{ 'Click a column to sort' | t }}</span>
        </div>
        <div class="table-scroll">
          <table class="perf">
            <thead>
              <tr>
                <th>{{ 'Product' | t }}</th>
                <th class="num"><button type="button" (click)="sortBy('views')" [class.on]="sortKey() === 'views'">{{ 'Views' | t }}</button></th>
                <th class="num">{{ 'Orders' | t }}</th>
                <th class="num"><button type="button" (click)="sortBy('conversionRate')" [class.on]="sortKey() === 'conversionRate'">{{ 'Conversion' | t }}</button></th>
                <th class="num"><button type="button" (click)="sortBy('revenue')" [class.on]="sortKey() === 'revenue'">{{ 'Revenue' | t }}</button></th>
                <th class="num"><button type="button" (click)="sortBy('returnRate')" [class.on]="sortKey() === 'returnRate'">{{ 'Returns' | t }}</button></th>
                <th class="num">{{ 'Stock' | t }}</th>
              </tr>
            </thead>
            <tbody>
              @for (p of sorted(); track p.productId) {
                <tr>
                  <td>
                    <a class="product" [routerLink]="['/seller/products', p.productId]">
                      @if (p.imageUrl) { <img [src]="p.imageUrl" alt="" /> }
                      <span>{{ p.name }}</span>
                    </a>
                    @if (insight(p); as tip) { <span class="tip">{{ tip | t }}</span> }
                  </td>
                  <td class="num">{{ p.views | number }}</td>
                  <td class="num">{{ p.orders | number }}</td>
                  <td class="num">
                    <span class="bar"><span [style.width.%]="barWidth(p.conversionRate)"></span></span>
                    {{ p.conversionRate | number: '1.1-1' }}%
                  </td>
                  <td class="num">{{ p.revenue | money }}</td>
                  <td class="num" [class.bad]="p.returnRate >= 15">{{ p.returnRate | number: '1.0-1' }}%</td>
                  <td class="num" [class.bad]="p.stock < 5">{{ p.stock }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }
  `,
  styles: `
    .kpis { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 12px; margin-bottom: 20px; }
    .kpi { display: flex; flex-direction: column; gap: 4px; padding: 16px 18px; border: 1px solid var(--mh-border); border-radius: 16px;
      background: var(--mh-surface); }
    .kpi span { color: var(--mh-text-muted); font-size: 13px; }
    .kpi strong { font-size: 24px; letter-spacing: -0.02em; }
    .kpi.accent { border-color: #ddd6fe; background: var(--mh-accent-soft); }
    .kpi.accent strong { color: var(--mh-accent); }
    .chart-panel { margin-bottom: 20px; }
    h2 { margin: 0 0 14px; font-size: 18px; }
    .table-head { display: flex; justify-content: space-between; align-items: baseline; }
    .small { font-size: 13px; }
    .table-scroll { overflow-x: auto; }
    table.perf { width: 100%; border-collapse: collapse; font-size: 14px; }
    th { padding: 10px 12px; border-bottom: 1px solid var(--mh-border); color: var(--mh-text-muted); font-size: 12px; font-weight: 600;
      text-align: start; text-transform: uppercase; letter-spacing: .04em; white-space: nowrap; }
    th button { padding: 0; border: 0; background: none; color: inherit; font: inherit; letter-spacing: inherit; text-transform: inherit; cursor: pointer; }
    th button.on { color: var(--mh-accent); }
    td { padding: 10px 12px; border-bottom: 1px solid var(--mh-border); vertical-align: middle; }
    .num { text-align: end; white-space: nowrap; font-variant-numeric: tabular-nums; }
    .product { display: flex; gap: 10px; align-items: center; color: var(--mh-text); font-weight: 500; text-decoration: none; }
    .product img { width: 36px; height: 36px; border-radius: 8px; object-fit: cover; }
    .tip { display: inline-block; margin-top: 4px; margin-inline-start: 46px; padding: 2px 8px; border-radius: 6px;
      background: var(--mh-warning-bg); color: var(--mh-warning); font-size: 12px; }
    .bar { display: inline-block; width: 60px; height: 6px; margin-inline-end: 8px; overflow: hidden; border-radius: 999px;
      background: var(--mh-surface-2); vertical-align: middle; }
    .bar span { display: block; height: 100%; background: var(--mh-accent); }
    .bad { color: var(--mh-danger); font-weight: 600; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SellerAnalyticsPage {
  private readonly api = inject(SellerApi);
  private readonly i18n = inject(I18nService);
  protected readonly data = signal<SellerAnalytics | null>(null);
  protected readonly days = signal(30);
  protected readonly loading = signal(false);
  protected readonly sortKey = signal<SortKey>('revenue');

  protected readonly sorted = computed(() => {
    const key = this.sortKey();
    return [...(this.data()?.products ?? [])].sort((a, b) => b[key] - a[key]);
  });
  private readonly maxConversion = computed(() => Math.max(1, ...(this.data()?.products ?? []).map((p) => p.conversionRate)));

  protected readonly chart = computed<ChartConfiguration<'bar' | 'line'>>(() => {
    const daily = this.data()?.daily ?? [];
    const locale = this.i18n.intlLocale;
    return {
      type: 'bar',
      data: {
        labels: daily.map((d) => new Date(d.date).toLocaleDateString(locale, { day: 'numeric', month: 'short' })),
        datasets: [
          { type: 'bar', label: this.i18n.t('Views'), data: daily.map((d) => d.views), backgroundColor: '#ddd6fe',
            borderRadius: 4, yAxisID: 'views' },
          { type: 'line', label: this.i18n.t('Orders'), data: daily.map((d) => d.orders), borderColor: '#6d28d9',
            backgroundColor: '#6d28d9', tension: 0.3, pointRadius: 0, borderWidth: 2.5, yAxisID: 'orders' },
        ],
      },
      options: {
        interaction: { mode: 'index', intersect: false },
        plugins: { legend: { position: 'bottom' } },
        scales: {
          x: { grid: { display: false }, ticks: { maxTicksLimit: 10 }, reverse: this.i18n.rtl },
          views: { position: this.i18n.rtl ? 'right' : 'left', beginAtZero: true, grid: { color: '#f1f5f9' } },
          orders: { position: this.i18n.rtl ? 'left' : 'right', beginAtZero: true, grid: { display: false },
            ticks: { precision: 0 } },
        },
      },
    };
  });

  constructor() {
    this.load(30);
  }

  protected load(days: number): void {
    this.days.set(days);
    this.loading.set(true);
    this.api.analytics(days).subscribe({
      next: (data) => {
        this.data.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected sortBy(key: SortKey): void {
    this.sortKey.set(key);
  }

  protected barWidth(rate: number): number {
    return (rate * 100) / this.maxConversion();
  }

  /** One actionable hint per product, from its own numbers only. */
  protected insight(p: ProductPerformance): string | null {
    if (p.views >= 50 && p.orders === 0) {
      return 'Many views, no order: check the price, photos and description';
    }
    if (p.returnRate >= 20 && p.orders + p.returns >= 5) {
      return 'High return rate: make the description and sizes clearer';
    }
    if (p.stock === 0 && p.views > 0) {
      return 'Out of stock while shoppers are looking: restock';
    }
    return null;
  }
}
