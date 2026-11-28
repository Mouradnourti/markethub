import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { I18nService } from '../../core/i18n/i18n.service';
import { WishlistService } from '../../core/wishlist/wishlist.service';

/** Heart toggle. "round" sits on product images; "outline" is the labelled button of the product page. */
@Component({
  selector: 'app-wishlist-button',
  imports: [MatIconModule],
  template: `
    <button type="button" [class]="'heart ' + variant()" [class.on]="saved()" (click)="toggle($event)"
            [attr.aria-pressed]="saved()" [attr.aria-label]="label()">
      <mat-icon [class.filled]="saved()">favorite</mat-icon>
      @if (variant() === 'outline') {
        <span>{{ saved() ? i18n.t('Saved') : i18n.t('Save') }}</span>
      }
    </button>
  `,
  styles: `
    .heart { display: inline-flex; gap: 6px; align-items: center; justify-content: center; border: 0; font: inherit; cursor: pointer;
      transition: transform .15s ease, background .15s ease, color .15s ease; }
    .heart:active { transform: scale(.92); }
    .round { width: 38px; height: 38px; border-radius: 50%; background: rgb(255 255 255 / .92); color: var(--mh-text);
      box-shadow: 0 6px 16px -8px rgb(0 0 0 / .4); }
    .round:hover { background: #fff; }
    .outline { height: 48px; padding: 0 18px; border: 1.5px solid var(--mh-border); border-radius: 999px; background: var(--mh-surface);
      color: var(--mh-text); font-weight: 600; }
    .outline:hover { border-color: var(--mh-text); }
    .on { color: var(--mh-sale); }
    mat-icon { width: 22px; height: 22px; font-size: 22px; }
    mat-icon.filled { font-variation-settings: 'FILL' 1; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WishlistButton {
  readonly productId = input.required<number>();
  readonly productName = input('');
  readonly variant = input<'round' | 'outline'>('round');
  protected readonly i18n = inject(I18nService);
  private readonly wishlist = inject(WishlistService);
  protected readonly saved = computed(() => this.wishlist.has(this.productId()));
  protected readonly label = computed(() =>
    this.i18n.t(this.saved() ? 'Remove {name} from your wishlist' : 'Save {name} to your wishlist', {
      name: this.productName(),
    }),
  );

  protected toggle(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.wishlist.toggle(this.productId());
  }
}
