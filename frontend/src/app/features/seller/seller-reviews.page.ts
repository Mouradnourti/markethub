import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { SellerApi } from '../../core/api/seller.api';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { SellerReview } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';
import { EmptyState } from '../../shared/ui/empty-state';
import { RatingStars } from '../../shared/ui/rating-stars';

/** Reviews of the seller's products, with public replies (the customer is notified). */
@Component({
  selector: 'app-seller-reviews-page',
  imports: [FormsModule, DatePipe, RouterLink, MatButtonModule, MatButtonToggleModule, MatFormFieldModule, MatInputModule,
    MatIconModule, RatingStars, EmptyState, TranslatePipe],
  template: `
    <div class="page-title">
      <div>
        <h1>{{ 'Reviews' | t }}</h1>
        <p>{{ 'Answer reviews publicly: shoppers trust shops that reply, especially to critical feedback.' | t }}</p>
      </div>
      <mat-button-toggle-group [value]="unanswered()" (change)="setFilter($event.value)">
        <mat-button-toggle [value]="true">{{ 'To answer' | t }}</mat-button-toggle>
        <mat-button-toggle [value]="false">{{ 'All reviews' | t }}</mat-button-toggle>
      </mat-button-toggle-group>
    </div>

    <div class="list">
      @for (r of reviews(); track r.id) {
        <article class="panel review">
          <a class="product" [routerLink]="['/products', r.productId]">
            @if (r.productImageUrl) { <img [src]="r.productImageUrl" alt="" /> }
            <span>{{ r.productName }}</span>
          </a>
          <div class="head">
            <app-rating-stars [value]="r.rating" />
            <strong>{{ r.authorName }}</strong>
            <span class="muted small">{{ r.createdAt | date: 'mediumDate' }}</span>
            @if (r.helpfulCount) { <span class="muted small"><mat-icon class="inline">thumb_up</mat-icon> {{ r.helpfulCount }}</span> }
          </div>
          @if (r.comment) { <p>{{ r.comment }}</p> } @else { <p class="muted">{{ 'Rating without comment.' | t }}</p> }
          @if (r.photoUrls.length) {
            <div class="photos">@for (url of r.photoUrls; track url) { <img [src]="url" alt="" /> }</div>
          }

          @if (replyingTo() === r.id) {
            <div class="reply-form">
              <mat-form-field class="full-width">
                <mat-label>{{ 'Your public reply' | t }}</mat-label>
                <textarea matInput rows="3" [(ngModel)]="draft" maxlength="1000"></textarea>
              </mat-form-field>
              <div class="row">
                <button mat-flat-button class="btn-ink" (click)="send(r)" [disabled]="!draft.trim() || busy()">{{ 'Publish reply' | t }}</button>
                <button mat-button (click)="replyingTo.set(null)">{{ 'Cancel' | t }}</button>
              </div>
            </div>
          } @else if (r.sellerReply) {
            <div class="reply">
              <strong><mat-icon>storefront</mat-icon> {{ 'Your reply' | t }} · {{ r.sellerRepliedAt | date: 'mediumDate' }}</strong>
              <p>{{ r.sellerReply }}</p>
              <div class="row">
                <button mat-button (click)="startReply(r)"><mat-icon>edit</mat-icon> {{ 'Edit' | t }}</button>
                <button mat-button (click)="deleteReply(r)"><mat-icon>delete</mat-icon> {{ 'Remove' | t }}</button>
              </div>
            </div>
          } @else {
            <button mat-stroked-button (click)="startReply(r)"><mat-icon>reply</mat-icon> {{ 'Reply' | t }}</button>
          }
        </article>
      } @empty {
        <div class="panel">
          <app-empty-state icon="task_alt" [title]="(unanswered() ? 'All caught up' : 'No reviews yet') | t"
                           [message]="(unanswered() ? 'Every review has an answer.' : 'Reviews appear here once customers receive their parcels.') | t" />
        </div>
      }
    </div>
  `,
  styles: `
    .list { display: grid; gap: 14px; }
    .review { display: flex; flex-direction: column; gap: 10px; padding: 20px; }
    .product { display: flex; gap: 10px; align-items: center; color: var(--mh-text); font-weight: 600; text-decoration: none; }
    .product img { width: 40px; height: 40px; border-radius: 10px; object-fit: cover; }
    .head { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
    .small { font-size: 13px; }
    .inline { width: 14px; height: 14px; font-size: 14px; vertical-align: -2px; }
    p { margin: 0; line-height: 1.6; }
    .photos { display: flex; gap: 8px; }
    .photos img { width: 64px; height: 64px; border-radius: 10px; object-fit: cover; }
    .reply { padding: 12px 14px; border-inline-start: 3px solid var(--mh-accent); border-radius: 10px; background: var(--mh-surface-2); }
    .reply strong { display: flex; gap: 6px; align-items: center; font-size: 13px; }
    .reply mat-icon { width: 16px; height: 16px; font-size: 16px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SellerReviewsPage {
  private readonly api = inject(SellerApi);
  private readonly notifier = inject(Notifier);
  private readonly i18n = inject(I18nService);
  protected readonly reviews = signal<SellerReview[]>([]);
  protected readonly unanswered = signal(true);
  protected readonly replyingTo = signal<number | null>(null);
  protected readonly busy = signal(false);
  protected draft = '';

  constructor() {
    this.reload();
  }

  protected setFilter(unanswered: boolean): void {
    this.unanswered.set(unanswered);
    this.reload();
  }

  protected startReply(r: SellerReview): void {
    this.draft = r.sellerReply ?? '';
    this.replyingTo.set(r.id);
  }

  protected send(r: SellerReview): void {
    this.busy.set(true);
    this.api.replyToReview(r.id, this.draft.trim()).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.replyingTo.set(null);
        this.notifier.success(this.i18n.t('Reply published. The customer has been notified.'));
        this.replace(updated);
      },
      error: () => this.busy.set(false),
    });
  }

  protected deleteReply(r: SellerReview): void {
    this.api.deleteReply(r.id).subscribe((updated) => this.replace(updated));
  }

  private replace(updated: SellerReview): void {
    if (this.unanswered() && updated.sellerReply) {
      this.reviews.update((list) => list.filter((r) => r.id !== updated.id));
    } else {
      this.reviews.update((list) => list.map((r) => (r.id === updated.id ? updated : r)));
    }
  }

  private reload(): void {
    this.api.reviews(this.unanswered(), 0, 50).subscribe((page) => this.reviews.set(page.content));
  }
}
