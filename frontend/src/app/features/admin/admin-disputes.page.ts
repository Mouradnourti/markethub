import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { AdminApi } from '../../core/api/admin.api';
import { Dispute, DisputeStatus, Page } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { humanize } from '../../shared/status';
import { EmptyState } from '../../shared/ui/empty-state';
import { StatusChip } from '../../shared/ui/status-chip';

@Component({
  selector: 'app-admin-disputes-page',
  imports: [DatePipe, MatButtonModule, MatButtonToggleModule, MatIconModule, MatPaginatorModule, MoneyPipe, StatusChip, EmptyState],
  template: `
    <div class="page-title"><div><h1>Returns & disputes</h1><p>Approving a return marks the parcel as returned, restocks the items and refunds the payment.</p></div></div>
    <mat-button-toggle-group [value]="filter()" (change)="filter.set($event.value); load(0)" class="filters" aria-label="Status filter">
      <mat-button-toggle value="OPEN">Open</mat-button-toggle>
      <mat-button-toggle value="APPROVED">Approved</mat-button-toggle>
      <mat-button-toggle value="REJECTED">Rejected</mat-button-toggle>
      <mat-button-toggle [value]="null">All</mat-button-toggle>
    </mat-button-toggle-group>

    @if (page(); as p) {
      @for (d of p.content; track d.id) {
        <article class="panel dispute">
          <div class="head">
            <div>
              <div class="row"><app-status-chip [status]="d.status" /> <strong>{{ d.type === 'RETURN' ? 'Return request' : 'Dispute' }}</strong>
                <span class="muted">· {{ d.createdAt | date: 'medium' }}</span></div>
              <h3>Order {{ d.orderReference }} · {{ d.shopName }}</h3>
              <span class="muted">Customer: {{ d.customerName }} · Parcel {{ humanize(d.shopOrderStatus).toLowerCase() }} · {{ d.amount | money }}</span>
            </div>
            @if (d.status === 'OPEN') {
              <div class="row">
                <button mat-flat-button (click)="resolve(d, 'APPROVED')"><mat-icon>check</mat-icon> Approve</button>
                <button mat-stroked-button (click)="resolve(d, 'REJECTED')"><mat-icon>close</mat-icon> Reject</button>
              </div>
            }
          </div>
          <blockquote>“{{ d.reason }}”</blockquote>
          @if (d.adminNote) { <p class="note"><mat-icon>admin_panel_settings</mat-icon> {{ d.adminNote }}</p> }
        </article>
      } @empty {
        <div class="panel"><app-empty-state icon="task_alt" title="Nothing to review" message="There is no request with this status." /></div>
      }
      @if (p.totalPages > 1) {
        <mat-paginator [length]="p.totalElements" [pageIndex]="p.page" [pageSize]="p.size" [hidePageSize]="true" (page)="load($event.pageIndex)" />
      }
    }
  `,
  styles: `
    .filters { margin-bottom: 16px; }
    .dispute { margin-bottom: 12px; }
    .head { display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; align-items: flex-start; }
    h3 { margin: 8px 0 2px; font-size: 16px; }
    blockquote { margin: 12px 0 0; padding: 10px 14px; border-left: 3px solid var(--mh-accent); background: var(--mh-surface-2); border-radius: 0 10px 10px 0; }
    .note { display: flex; gap: 6px; align-items: center; margin: 10px 0 0; color: var(--mh-text-muted); font-size: 14px; }
    mat-paginator { background: transparent; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminDisputesPage {
  private readonly api = inject(AdminApi);
  private readonly notifier = inject(Notifier);
  protected readonly filter = signal<DisputeStatus | null>('OPEN');
  protected readonly page = signal<Page<Dispute> | null>(null);
  protected readonly humanize = humanize;

  constructor() {
    this.load(0);
  }

  protected load(pageIndex: number): void {
    this.api.disputes(this.filter(), pageIndex, 10).subscribe((page) => this.page.set(page));
  }

  protected resolve(dispute: Dispute, decision: DisputeStatus): void {
    const note = prompt(decision === 'APPROVED' ? 'Note for the customer (optional)' : 'Reason for rejecting (optional)', '');
    if (note === null) {
      return;
    }
    this.api.resolveDispute(dispute.id, decision, note).subscribe(() => {
      this.notifier.success(`Request ${decision === 'APPROVED' ? 'approved' : 'rejected'}`);
      this.load(this.page()?.page ?? 0);
    });
  }
}
