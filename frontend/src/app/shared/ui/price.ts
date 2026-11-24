import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MoneyPipe } from '../pipes/money.pipe';

/** Percentage saved against the compare-at price, or 0 when the product is not discounted. */
export function discountPercent(price: number, compareAtPrice?: number | null): number {
  if (!compareAtPrice || compareAtPrice <= price) {
    return 0;
  }
  return Math.round((1 - price / compareAtPrice) * 100);
}

/** Selling price, with the original price struck through while the product is on sale. */
@Component({
  selector: 'app-price',
  imports: [MoneyPipe],
  template: `
    <span class="price-now" [class.on-sale]="onSale()">{{ price() | money }}</span>
    @if (onSale()) {
      <span class="price-was"><span class="sr-only">Was </span>{{ compareAtPrice() | money }}</span>
    }
  `,
  styles: `
    :host { display: inline-flex; align-items: baseline; flex-wrap: wrap; font-size: var(--price-size, 16px); }
    .price-was { font-size: 0.82em; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Price {
  readonly price = input.required<number>();
  readonly compareAtPrice = input<number | null | undefined>();
  protected readonly onSale = computed(() => discountPercent(this.price(), this.compareAtPrice()) > 0);
}
