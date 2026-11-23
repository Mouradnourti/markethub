import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';

/** Read-only stars, or an input when {@code editable} is set (two-way bound through {@code value}). */
@Component({
  selector: 'app-rating-stars',
  template: `
    <span class="stars" [class.editable]="editable()" [attr.aria-label]="ariaLabel()">
      @for (star of stars; track star) {
        @if (editable()) {
          <button type="button" class="star" (click)="value.set(star)" [attr.aria-label]="star + ' stars'">
            <span class="material-symbols-outlined" [class.filled]="star <= value()">star</span>
          </button>
        } @else {
          <span class="material-symbols-outlined star" [class.filled]="star <= rounded()"
                [class.half]="star === halfStar()">{{ star === halfStar() ? 'star_half' : 'star' }}</span>
        }
      }
      @if (count() !== undefined) {
        <span class="count">({{ count() }})</span>
      }
    </span>
  `,
  styles: `
    .stars { display: inline-flex; align-items: center; gap: 1px; color: #f59e0b; }
    .star { font-size: var(--star-size, 18px); color: #d6d3d1; background: none; border: 0; padding: 0; }
    .star.filled, .star .filled, .star.half { color: #f59e0b; font-variation-settings: 'FILL' 1; }
    .editable .star { cursor: pointer; }
    .editable .material-symbols-outlined { font-size: 28px; color: #d6d3d1; }
    .editable .material-symbols-outlined.filled { color: #f59e0b; font-variation-settings: 'FILL' 1; }
    .count { margin-left: 4px; font-size: 12px; color: var(--mh-text-muted); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RatingStars {
  readonly value = model(0);
  readonly count = input<number>();
  readonly editable = input(false);
  protected readonly stars = [1, 2, 3, 4, 5];
  protected readonly rounded = computed(() => Math.floor(this.value()));
  protected readonly halfStar = computed(() => (this.value() - Math.floor(this.value()) >= 0.5 ? Math.floor(this.value()) + 1 : -1));
  protected readonly ariaLabel = computed(() => `${this.value().toFixed(1)} out of 5`);
}
