import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  input,
  viewChild,
} from '@angular/core';
import { Chart, ChartConfiguration, registerables } from 'chart.js';

Chart.register(...registerables);
Chart.defaults.font.family = "Inter, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif";
Chart.defaults.color = '#64748b';

/** Thin Chart.js wrapper: re-renders whenever the configuration signal changes. */
@Component({
  selector: 'app-chart',
  template: `<div class="frame" [style.height.px]="height()"><canvas #canvas></canvas></div>`,
  styles: `.frame { position: relative; width: 100%; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChartComponent implements OnDestroy {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  readonly config = input.required<ChartConfiguration<any>>();
  readonly height = input(280);
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart;

  constructor() {
    effect(() => {
      const config = this.config();
      this.chart?.destroy();
      this.chart = new Chart(this.canvas().nativeElement, {
        ...config,
        options: { responsive: true, maintainAspectRatio: false, ...config.options },
      });
    });
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }
}

/** Categorical palette shared by every chart (validated for contrast on light backgrounds). */
export const CHART_COLORS = ['#4f46e5', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#64748b'];
