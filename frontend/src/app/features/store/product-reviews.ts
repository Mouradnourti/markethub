import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { CatalogApi } from '../../core/api/catalog.api';
import { AuthService } from '../../core/auth/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { Review, ReviewEligibility, ReviewSort, ReviewSummary } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';
import { RatingStars } from '../../shared/ui/rating-stars';

const PAGE_SIZE = 6;

/** Verified reviews: star breakdown, sort and filter, helpful votes, customer photos and seller replies. */
@Component({
  selector: 'app-product-reviews',
  imports: [FormsModule, DatePipe, DecimalPipe, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatIconModule, RatingStars, TranslatePipe],
  template: `
    <div class="summary">
      <h2>{{ 'Customer reviews' | t }}</h2>
      @if (summary(); as s) {
        <div class="score-line">
          <span class="score">{{ s.average | number: '1.1-1' }}</span>
          <div>
            <app-rating-stars [value]="s.average" />
            <span class="muted small">{{ 'Based on {count} verified reviews' | t: { count: s.total } }}</span>
          </div>
        </div>
        <ul class="bars">
          @for (stars of [5, 4, 3, 2, 1]; track stars) {
            @let count = s.counts[stars - 1];
            <li>
              <button type="button" (click)="filterBy(stars)" [class.on]="rating() === stars" [disabled]="!count"
                      [attr.aria-label]="'Show {stars}-star reviews ({count})' | t: { stars, count }">
                <span class="label">{{ stars }} <mat-icon>star</mat-icon></span>
                <span class="track"><span class="fill" [style.width.%]="s.total ? (count * 100) / s.total : 0"></span></span>
                <span class="n">{{ count }}</span>
              </button>
            </li>
          }
        </ul>
        @if (s.withPhotos) {
          <p class="muted small"><mat-icon class="inline">photo_camera</mat-icon> {{ '{count} reviews include photos' | t: { count: s.withPhotos } }}</p>
        }
      }
      <p class="muted small">{{ 'Only customers who received this product can review it.' | t }}</p>

      @if (eligibility()?.canReview) {
        <form class="review-form" (ngSubmit)="submit()">
          <strong>{{ 'Share your opinion' | t }}</strong>
          <app-rating-stars [editable]="true" [(value)]="newRating" />
          <mat-form-field class="full-width">
            <mat-label>{{ 'Your review (optional)' | t }}</mat-label>
            <textarea matInput rows="3" name="comment" [(ngModel)]="newComment" maxlength="2000"></textarea>
          </mat-form-field>
          <button mat-flat-button class="btn-ink" [disabled]="submitting()">{{ 'Publish review' | t }}</button>
        </form>
      }
    </div>

    <div class="list-side">
      <div class="toolbar">
        <mat-form-field class="sort" subscriptSizing="dynamic">
          <mat-label>{{ 'Sort by' | t }}</mat-label>
          <mat-select [value]="sort()" (valueChange)="changeSort($event)">
            <mat-option value="HELPFUL">{{ 'Most helpful' | t }}</mat-option>
            <mat-option value="RECENT">{{ 'Most recent' | t }}</mat-option>
            <mat-option value="HIGHEST">{{ 'Highest rating' | t }}</mat-option>
            <mat-option value="LOWEST">{{ 'Lowest rating' | t }}</mat-option>
          </mat-select>
        </mat-form-field>
        @if (rating(); as stars) {
          <button type="button" class="chip" (click)="filterBy(null)">
            {{ '{stars} stars' | t: { stars } }} <mat-icon>close</mat-icon>
          </button>
        }
      </div>

      <div class="review-list">
        @for (review of reviews(); track review.id) {
          <article class="review">
            <div class="review-head">
              <span class="avatar" aria-hidden="true">{{ review.authorName.charAt(0) }}</span>
              <div>
                <strong>{{ review.authorName }}</strong>
                <div class="verified"><mat-icon>verified</mat-icon> {{ 'Verified purchase' | t }}</div>
              </div>
              <span class="spacer"></span>
              <span class="muted small">{{ review.createdAt | date: 'mediumDate' }}</span>
            </div>
            <app-rating-stars [value]="review.rating" />
            @if (review.comment) {
              <p>{{ review.comment }}</p>
            }
            @if (review.photoUrls.length) {
              <div class="photos">
                @for (url of review.photoUrls; track url) {
                  <a [href]="url" target="_blank" rel="noopener"><img [src]="url" [alt]="'Photo from {name}' | t: { name: review.authorName }" loading="lazy" /></a>
                }
              </div>
            }
            @if (review.sellerReply) {
              <div class="reply">
                <strong><mat-icon>storefront</mat-icon> {{ 'Reply from {shop}' | t: { shop: shopName() } }}</strong>
                <p>{{ review.sellerReply }}</p>
              </div>
            }
            <div class="actions">
              @if (review.mine) {
                @if (review.photoUrls.length < 3) {
                  <label class="link">
                    <mat-icon>add_a_photo</mat-icon> {{ 'Add a photo' | t }}
                    <input type="file" accept="image/jpeg,image/png,image/webp" hidden (change)="addPhoto(review, $event)" />
                  </label>
                }
              } @else {
                <button type="button" class="helpful" [class.on]="review.votedHelpful" (click)="vote(review)"
                        [attr.aria-pressed]="review.votedHelpful">
                  <mat-icon>thumb_up</mat-icon> {{ 'Helpful' | t }}
                  @if (review.helpfulCount) { <span>({{ review.helpfulCount }})</span> }
                </button>
              }
            </div>
          </article>
        } @empty {
          <p class="muted">{{ 'No reviews yet. Be the first to share your experience after delivery.' | t }}</p>
        }
      </div>
      @if (reviews().length < total()) {
        <button mat-stroked-button class="more" (click)="loadMore()">{{ 'Show more reviews' | t }}</button>
      }
    </div>
  `,
  styleUrl: './product-reviews.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductReviews {
  readonly productId = input.required<number>();
  readonly shopName = input('');
  private readonly catalog = inject(CatalogApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);
  private readonly i18n = inject(I18nService);

  protected readonly summary = signal<ReviewSummary | null>(null);
  protected readonly reviews = signal<Review[]>([]);
  protected readonly total = signal(0);
  protected readonly sort = signal<ReviewSort>('HELPFUL');
  protected readonly rating = signal<number | null>(null);
  protected readonly eligibility = signal<ReviewEligibility | null>(null);
  protected readonly newRating = signal(5);
  protected readonly submitting = signal(false);
  protected newComment = '';
  private page = 0;
  protected readonly hasReviews = computed(() => (this.summary()?.total ?? 0) > 0);

  constructor() {
    effect(() => {
      const id = this.productId();
      this.catalog.reviewSummary(id).subscribe({ next: (s) => this.summary.set(s), error: () => undefined });
      if (this.auth.role() === 'CUSTOMER') {
        this.catalog.reviewEligibility(id).subscribe((e) => this.eligibility.set(e));
      }
      this.reload();
    });
  }

  protected changeSort(sort: ReviewSort): void {
    this.sort.set(sort);
    this.reload();
  }

  protected filterBy(stars: number | null): void {
    this.rating.set(this.rating() === stars ? null : stars);
    this.reload();
  }

  protected loadMore(): void {
    this.page++;
    this.fetch(true);
  }

  protected vote(review: Review): void {
    if (!this.auth.user()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    if (this.auth.role() !== 'CUSTOMER') {
      return;
    }
    this.catalog.voteHelpful(this.productId(), review.id, !review.votedHelpful).subscribe((updated) => this.replace(updated));
  }

  protected addPhoto(review: Review, event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) {
      return;
    }
    this.catalog.addReviewPhoto(this.productId(), review.id, file).subscribe((updated) => {
      this.replace(updated);
      this.notifier.success(this.i18n.t('Photo added to your review.'));
    });
  }

  protected submit(): void {
    this.submitting.set(true);
    this.catalog.addReview(this.productId(), this.newRating(), this.newComment.trim()).subscribe({
      next: () => {
        this.notifier.success(this.i18n.t('Thank you for your review!'));
        this.newComment = '';
        this.submitting.set(false);
        this.eligibility.set({ canReview: false });
        this.catalog.reviewSummary(this.productId()).subscribe((s) => this.summary.set(s));
        this.sort.set('RECENT');
        this.reload();
      },
      error: () => this.submitting.set(false),
    });
  }

  private reload(): void {
    this.page = 0;
    this.fetch(false);
  }

  private fetch(append: boolean): void {
    this.catalog
      .reviews(this.productId(), this.page, PAGE_SIZE, this.sort(), this.rating() ?? undefined)
      .subscribe((page) => {
        this.reviews.update((current) => (append ? [...current, ...page.content] : page.content));
        this.total.set(page.totalElements);
      });
  }

  private replace(updated: Review): void {
    this.reviews.update((list) => list.map((r) => (r.id === updated.id ? updated : r)));
  }
}
