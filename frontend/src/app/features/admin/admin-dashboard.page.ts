import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { ChartConfiguration } from 'chart.js';
import { AdminApi } from '../../core/api/admin.api';
import { AdminStats } from '../../core/models/api.models';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { humanize } from '../../shared/status';
import { CHART_COLORS, ChartComponent } from '../../shared/ui/chart';
import { StatCard } from '../../shared/ui/stat-card';

@Component({
  selector: 'app-admin-dashboard-page',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule, StatCard, ChartComponent],
  template: `
    <div class="page-title">
      <div><h1>Platform overview</h1><p>Marketplace health over the last 12 months</p></div>
    </div>
    @if (stats(); as s) {
      @if ((s.shopsByStatus['PENDING'] ?? 0) > 0 || s.openDisputes > 0) {
        <div class="todo">
          @if ((s.shopsByStatus['PENDING'] ?? 0) > 0) {
            <a routerLink="/admin/shops" [queryParams]="{ status: 'PENDING' }"><mat-icon>hourglass_top</mat-icon>
              {{ s.shopsByStatus['PENDING'] }} shop(s) awaiting validation</a>
          }
          @if (s.openDisputes > 0) {
            <a routerLink="/admin/disputes"><mat-icon>gavel</mat-icon> {{ s.openDisputes }} open return request(s)</a>
          }
        </div>
      }
      <div class="grid-stats">
        <app-stat-card label="Gross merchandise value" [value]="money(s.grossMerchandiseValue)" icon="account_balance" color="#4f46e5" />
        <app-stat-card label="Commission earned" [value]="money(s.commissionEarned)" icon="savings" color="#10b981" hint="On delivered parcels" />
        <app-stat-card label="Orders" [value]="s.totalOrders" icon="receipt_long" color="#0ea5e9" [hint]="'Average basket ' + money(s.averageOrderValue)" />
        <app-stat-card label="Customers" [value]="s.usersByRole['CUSTOMER'] ?? 0" icon="group" color="#f59e0b"
                       [hint]="(s.usersByRole['SELLER'] ?? 0) + ' sellers'" />
        <app-stat-card label="Active shops" [value]="s.shopsByStatus['APPROVED'] ?? 0" icon="storefront" color="#8b5cf6"
                       [hint]="(s.shopsByStatus['PENDING'] ?? 0) + ' pending'" />
      </div>

      <div class="grid-2 section">
        <section class="panel"><h2>GMV and commission per month</h2>
          @if (monthly(); as c) { <app-chart [config]="c" [height]="300" /> }</section>
        <section class="panel"><h2>Payment methods</h2>
          @if (payments(); as c) { <app-chart [config]="c" [height]="300" /> }</section>
      </div>
      <div class="grid-3 section">
        <section class="panel"><h2>Top categories</h2>
          @if (categories(); as c) { <app-chart [config]="c" [height]="260" /> }</section>
        <section class="panel"><h2>Top shops</h2>
          @if (shops(); as c) { <app-chart [config]="c" [height]="260" /> }</section>
        <section class="panel"><h2>Parcels by status</h2>
          @if (statuses(); as c) { <app-chart [config]="c" [height]="260" /> }</section>
      </div>
    } @else {
      <div class="loading"><mat-spinner diameter="40" /></div>
    }
  `,
  styles: `
    .section { margin-top: 16px; }
    h2 { margin: 0 0 12px; font-size: 17px; }
    .grid-3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    @media (max-width: 1100px) { .grid-3 { grid-template-columns: 1fr; } }
    .todo { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
    .todo a { display: inline-flex; gap: 8px; align-items: center; padding: 10px 14px; border-radius: 12px;
      background: var(--mh-warning-bg); color: var(--mh-warning); font-weight: 600; text-decoration: none; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminDashboardPage {
  private readonly moneyPipe = new MoneyPipe();
  protected readonly stats = signal<AdminStats | null>(null);

  protected readonly monthly = computed<ChartConfiguration<'bar' | 'line'> | null>(() => {
    const s = this.stats();
    if (!s) {
      return null;
    }
    return {
      type: 'bar',
      data: {
        labels: s.monthly.map((m) => m.month),
        datasets: [
          { type: 'bar', label: 'GMV', data: s.monthly.map((m) => m.revenue), backgroundColor: CHART_COLORS[0], borderRadius: 6, yAxisID: 'y' },
          { type: 'line', label: 'Commission', data: s.monthly.map((m) => m.commission), borderColor: CHART_COLORS[2],
            backgroundColor: CHART_COLORS[2], tension: 0.3, yAxisID: 'y1' },
        ],
      } as ChartConfiguration<'bar' | 'line'>['data'],
      options: {
        plugins: { legend: { position: 'bottom' } },
        scales: { y: { beginAtZero: true }, y1: { beginAtZero: true, position: 'right', grid: { drawOnChartArea: false } } },
      },
    };
  });

  protected readonly payments = computed(() => this.doughnut(this.stats()?.paymentMethods, (k) => (k === 'COD' ? 'Cash on delivery' : 'Card')));
  protected readonly statuses = computed(() => this.doughnut(this.stats()?.ordersByStatus, humanize));
  protected readonly categories = computed(() => this.horizontalBars(this.stats()?.topCategories, CHART_COLORS[1]));
  protected readonly shops = computed(() => this.horizontalBars(this.stats()?.topShops, CHART_COLORS[5]));

  constructor() {
    inject(AdminApi).stats().subscribe((stats) => this.stats.set(stats));
  }

  protected money(value: number): string {
    return this.moneyPipe.transform(value);
  }

  private doughnut(values: Record<string, number> | undefined, label: (key: string) => string): ChartConfiguration<'doughnut'> | null {
    if (!values) {
      return null;
    }
    const entries = Object.entries(values);
    return {
      type: 'doughnut',
      data: {
        labels: entries.map(([key]) => label(key)),
        datasets: [{ data: entries.map(([, v]) => v), backgroundColor: CHART_COLORS, borderWidth: 0 }],
      },
      options: { cutout: '62%', plugins: { legend: { position: 'bottom' } } },
    };
  }

  private horizontalBars(values: { label: string; value: number }[] | undefined, color: string): ChartConfiguration<'bar'> | null {
    if (!values) {
      return null;
    }
    return {
      type: 'bar',
      data: {
        labels: values.map((v) => v.label),
        datasets: [{ label: 'Revenue', data: values.map((v) => v.value), backgroundColor: color, borderRadius: 6 }],
      },
      options: { indexAxis: 'y', plugins: { legend: { display: false } } },
    };
  }
}
