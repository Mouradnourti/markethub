import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { ChartConfiguration } from 'chart.js';
import { SellerApi } from '../../core/api/seller.api';
import { SalesForecast, SellerStats, Shop } from '../../core/models/api.models';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { humanize } from '../../shared/status';
import { CHART_COLORS, ChartComponent } from '../../shared/ui/chart';
import { StatCard } from '../../shared/ui/stat-card';
import { ShopStatusBanner } from './shop-status-banner';

@Component({
  selector: 'app-seller-dashboard-page',
  imports: [
    RouterLink,
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MoneyPipe,
    StatCard,
    ChartComponent,
    ShopStatusBanner,
  ],
  templateUrl: './seller-dashboard.page.html',
  styleUrl: './seller-dashboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SellerDashboardPage {
  private readonly api = inject(SellerApi);
  private readonly money = new MoneyPipe();
  protected readonly shop = signal<Shop | null>(null);
  protected readonly stats = signal<SellerStats | null>(null);
  protected readonly forecast = signal<SalesForecast | null>(null);

  protected readonly monthlyChart = computed<ChartConfiguration<'bar'> | null>(() => {
    const stats = this.stats();
    if (!stats) {
      return null;
    }
    return {
      type: 'bar',
      data: {
        labels: stats.monthly.map((m) => this.monthLabel(m.month)),
        datasets: [
          { label: 'Revenue', data: stats.monthly.map((m) => m.revenue), backgroundColor: CHART_COLORS[0], borderRadius: 6 },
          { label: 'Commission', data: stats.monthly.map((m) => m.commission), backgroundColor: CHART_COLORS[1], borderRadius: 6 },
        ],
      },
      options: { plugins: { legend: { position: 'bottom' } }, scales: { y: { beginAtZero: true } } },
    };
  });

  protected readonly statusChart = computed<ChartConfiguration<'doughnut'> | null>(() => {
    const stats = this.stats();
    if (!stats) {
      return null;
    }
    const entries = Object.entries(stats.ordersByStatus);
    return {
      type: 'doughnut',
      data: {
        labels: entries.map(([status]) => humanize(status)),
        datasets: [{ data: entries.map(([, count]) => count), backgroundColor: CHART_COLORS, borderWidth: 0 }],
      },
      options: { cutout: '65%', plugins: { legend: { position: 'right' } } },
    };
  });

  protected readonly forecastChart = computed<ChartConfiguration<'line'> | null>(() => {
    const forecast = this.forecast();
    if (!forecast?.available) {
      return null;
    }
    const history = forecast.history.slice(-60);
    const labels = [...history, ...forecast.forecast].map((p) => p.date.slice(5));
    const pad = (n: number) => Array<number | null>(n).fill(null);
    const lastActual = history.at(-1)?.revenue ?? null;
    return {
      type: 'line',
      data: {
        labels,
        datasets: [
          { label: 'Actual daily revenue', data: [...history.map((p) => p.revenue), ...pad(forecast.forecast.length)],
            borderColor: CHART_COLORS[0], backgroundColor: CHART_COLORS[0], pointRadius: 0, tension: 0.3 },
          { label: 'AI forecast', data: [...pad(history.length - 1), lastActual, ...forecast.forecast.map((p) => p.revenue)],
            borderColor: CHART_COLORS[2], borderDash: [6, 4], pointRadius: 0, tension: 0.3 },
          { label: 'Upper bound', data: [...pad(history.length), ...forecast.forecast.map((p) => p.upper ?? null)],
            borderColor: 'transparent', backgroundColor: 'rgba(16,185,129,0.12)', pointRadius: 0, fill: '+1' },
          { label: 'Lower bound', data: [...pad(history.length), ...forecast.forecast.map((p) => p.lower ?? null)],
            borderColor: 'transparent', pointRadius: 0, fill: false },
        ],
      },
      options: {
        interaction: { mode: 'index', intersect: false },
        plugins: { legend: { position: 'bottom', labels: { filter: (item) => !item.text.includes('bound') } } },
        scales: { y: { beginAtZero: true }, x: { ticks: { maxTicksLimit: 12 } } },
      },
    };
  });

  protected readonly forecastTotal = computed(() =>
    (this.forecast()?.forecast ?? []).reduce((sum, point) => sum + point.revenue, 0),
  );

  constructor() {
    this.api.shop().subscribe((shop) => {
      this.shop.set(shop);
      if (shop.status === 'APPROVED') {
        this.api.stats().subscribe((stats) => this.stats.set(stats));
        this.api.forecast(30).subscribe({
          next: (f) => this.forecast.set(f),
          error: () => this.forecast.set({ available: false, history: [], forecast: [], message: 'AI service unavailable' }),
        });
      }
    });
  }

  protected format(value: number): string {
    return this.money.transform(value);
  }

  private monthLabel(month: string): string {
    const [year, m] = month.split('-').map(Number);
    return new Date(year, m - 1, 1).toLocaleDateString('en-US', { month: 'short', year: '2-digit' });
  }
}
