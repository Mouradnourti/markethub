import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { CatalogApi } from '../core/api/catalog.api';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { Suggestions } from '../core/models/api.models';
import { MoneyPipe } from '../shared/pipes/money.pipe';

interface Option {
  kind: 'product' | 'category' | 'search';
  id?: number;
  label: string;
}

/** Header search with instant, typo-tolerant suggestions (combobox pattern, keyboard friendly). */
@Component({
  selector: 'app-search-box',
  imports: [FormsModule, MatIconModule, MoneyPipe, TranslatePipe],
  template: `
    <form class="search" (ngSubmit)="submit()" role="search" (focusin)="open.set(true)">
      <mat-icon aria-hidden="true">search</mat-icon>
      <input name="q" [ngModel]="query()" (ngModelChange)="onInput($event)" autocomplete="off"
             [placeholder]="'Search products, brands and shops' | t" [attr.aria-label]="'Search' | t"
             role="combobox" aria-autocomplete="list" aria-controls="search-suggestions"
             [attr.aria-expanded]="showPanel()" [attr.aria-activedescendant]="active() >= 0 ? 'sugg-' + active() : null"
             (keydown)="onKey($event)" />
      @if (query()) {
        <button type="button" class="clear" (click)="clear()" [attr.aria-label]="'Clear' | t"><mat-icon>close</mat-icon></button>
      }
      <button type="submit" class="search-go" [attr.aria-label]="'Search' | t"><mat-icon>arrow_forward</mat-icon></button>
    </form>
    @if (showPanel()) {
      <div class="panel" id="search-suggestions" role="listbox">
        @let s = suggestions()!;
        @if (s.categories.length) {
          <div class="group">{{ 'Categories' | t }}</div>
          @for (category of s.categories; track category.id; let i = $index) {
            <button type="button" role="option" class="row cat" [id]="'sugg-' + i" [class.active]="active() === i"
                    (mousedown)="$event.preventDefault()" (click)="pick({ kind: 'category', id: category.id, label: category.name })">
              <mat-icon>category</mat-icon><span>{{ category.name }}</span>
            </button>
          }
        }
        @if (s.products.length) {
          <div class="group">{{ 'Products' | t }}</div>
          @for (product of s.products; track product.id; let i = $index) {
            @let index = s.categories.length + i;
            <button type="button" role="option" class="row" [id]="'sugg-' + index" [class.active]="active() === index"
                    (mousedown)="$event.preventDefault()" (click)="pick({ kind: 'product', id: product.id, label: product.name })">
              @if (product.imageUrl) { <img [src]="product.imageUrl" alt="" /> }
              <span class="name">{{ product.name }}<small>{{ product.shopName }}</small></span>
              <strong>{{ product.price | money }}</strong>
            </button>
          }
        }
        <button type="button" role="option" class="row all" [id]="'sugg-' + optionCount()"
                [class.active]="active() === optionCount()" (mousedown)="$event.preventDefault()" (click)="submit()">
          <mat-icon>search</mat-icon><span>{{ 'See all results for “{q}”' | t: { q: query() } }}</span>
        </button>
      </div>
    }
  `,
  styles: `
    :host { position: relative; display: block; flex: 1; max-width: 620px; }
    .search { display: flex; align-items: center; gap: 6px; height: 46px; padding: 0 6px 0 14px; border: 1.5px solid var(--mh-border);
      border-radius: 999px; background: var(--mh-surface-2); transition: border-color .15s, background .15s, box-shadow .15s; }
    .search:focus-within { border-color: var(--mh-ink); background: var(--mh-surface); box-shadow: 0 0 0 4px rgb(17 17 17 / .06); }
    .search > mat-icon { color: var(--mh-text-muted); }
    input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; font: inherit; font-size: 15px; color: var(--mh-text); }
    .clear, .search-go { display: grid; place-items: center; width: 34px; height: 34px; border: 0; border-radius: 50%; cursor: pointer; }
    .clear { background: transparent; color: var(--mh-text-muted); }
    .search-go { background: var(--mh-ink); color: #fff; }
    .clear mat-icon, .search-go mat-icon { width: 18px; height: 18px; font-size: 18px; }
    :host-context([dir='rtl']) .search-go mat-icon { transform: scaleX(-1); }
    .panel { position: absolute; top: calc(100% + 8px); inset-inline: 0; z-index: 40; max-height: 70vh; overflow-y: auto; padding: 8px;
      border: 1px solid var(--mh-border); border-radius: 18px; background: var(--mh-surface); box-shadow: 0 24px 48px -20px rgb(0 0 0 / .35); }
    .group { padding: 8px 10px 4px; color: var(--mh-text-muted); font-size: 11px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
    .row { display: flex; gap: 12px; align-items: center; width: 100%; padding: 8px 10px; border: 0; border-radius: 12px;
      background: transparent; color: var(--mh-text); font: inherit; text-align: start; cursor: pointer; }
    .row:hover, .row.active { background: var(--mh-surface-2); }
    .row img { width: 42px; height: 42px; border-radius: 10px; object-fit: cover; background: var(--mh-surface-2); }
    .row .name { display: flex; flex: 1; flex-direction: column; min-width: 0; font-size: 14px; font-weight: 500; }
    .row .name small { color: var(--mh-text-muted); font-size: 12px; font-weight: 400; }
    .row strong { font-size: 14px; }
    .row mat-icon { color: var(--mh-text-muted); }
    .all { margin-top: 4px; border-top: 1px solid var(--mh-border); border-radius: 0 0 12px 12px; font-weight: 600; }
  `,
  host: { '(document:click)': 'onDocumentClick($event)' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchBox {
  private readonly api = inject(CatalogApi);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly input$ = new Subject<string>();

  protected readonly query = signal('');
  protected readonly suggestions = signal<Suggestions | null>(null);
  protected readonly open = signal(false);
  protected readonly active = signal(-1);

  constructor() {
    this.input$
      .pipe(
        debounceTime(180),
        distinctUntilChanged(),
        switchMap((q) => (q.trim().length < 2 ? of(null) : this.api.suggest(q.trim()).pipe(catchError(() => of(null))))),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        this.suggestions.set(result);
        this.active.set(-1);
      });
  }

  protected showPanel(): boolean {
    const s = this.suggestions();
    return this.open() && this.query().trim().length >= 2 && !!s && (s.products.length > 0 || s.categories.length > 0);
  }

  protected optionCount(): number {
    const s = this.suggestions();
    return s ? s.categories.length + s.products.length : 0;
  }

  protected onInput(value: string): void {
    this.query.set(value);
    this.open.set(true);
    this.input$.next(value);
  }

  protected onKey(event: KeyboardEvent): void {
    if (!this.showPanel()) {
      return;
    }
    const last = this.optionCount();
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.active.update((i) => (i >= last ? 0 : i + 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.active.update((i) => (i <= 0 ? last : i - 1));
    } else if (event.key === 'Escape') {
      this.open.set(false);
    } else if (event.key === 'Enter' && this.active() >= 0) {
      event.preventDefault();
      const option = this.optionAt(this.active());
      if (option) {
        this.pick(option);
      } else {
        this.submit();
      }
    }
  }

  protected pick(option: Option): void {
    this.open.set(false);
    if (option.kind === 'product') {
      void this.router.navigate(['/products', option.id]);
    } else if (option.kind === 'category') {
      void this.router.navigate(['/products'], { queryParams: { categoryId: option.id } });
    }
  }

  protected submit(): void {
    this.open.set(false);
    const q = this.query().trim();
    void this.router.navigate(['/products'], { queryParams: { q: q || null, sort: q ? 'RELEVANCE' : null } });
  }

  protected clear(): void {
    this.query.set('');
    this.suggestions.set(null);
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  private optionAt(index: number): Option | null {
    const s = this.suggestions();
    if (!s) {
      return null;
    }
    if (index < s.categories.length) {
      const c = s.categories[index];
      return { kind: 'category', id: c.id, label: c.name };
    }
    const p = s.products[index - s.categories.length];
    return p ? { kind: 'product', id: p.id, label: p.name } : null;
  }
}
