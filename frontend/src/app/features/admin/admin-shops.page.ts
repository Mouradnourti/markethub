import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { AdminApi } from '../../core/api/admin.api';
import { Page, ShopAdmin, ShopStatus } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';
import { humanize } from '../../shared/status';
import { StatusChip } from '../../shared/ui/status-chip';

@Component({
  selector: 'app-admin-shops-page',
  imports: [DatePipe, MatTableModule, MatPaginatorModule, MatButtonModule, MatButtonToggleModule, MatIconModule, MatMenuModule, StatusChip],
  template: `
    <div class="page-title"><div><h1>Sellers & shops</h1><p>Validate new sellers and set commissions.</p></div></div>
    <mat-button-toggle-group [value]="filter()" (change)="setFilter($event.value)" class="filters" aria-label="Status filter">
      @for (s of statuses; track s) { <mat-button-toggle [value]="s">{{ s ? humanize(s) : 'All' }}</mat-button-toggle> }
    </mat-button-toggle-group>
    <section class="panel table-wrap">
      @if (page(); as p) {
        <table mat-table [dataSource]="p.content">
          <ng-container matColumnDef="shop">
            <th mat-header-cell *matHeaderCellDef>Shop</th>
            <td mat-cell *matCellDef="let row"><strong>{{ row.name }}</strong><div class="muted small">since {{ row.createdAt | date: 'mediumDate' }}</div></td>
          </ng-container>
          <ng-container matColumnDef="owner">
            <th mat-header-cell *matHeaderCellDef>Owner</th>
            <td mat-cell *matCellDef="let row">{{ row.ownerName }}<div class="muted small">{{ row.ownerEmail }}</div></td>
          </ng-container>
          <ng-container matColumnDef="products">
            <th mat-header-cell *matHeaderCellDef>Products</th>
            <td mat-cell *matCellDef="let row">{{ row.productCount }}</td>
          </ng-container>
          <ng-container matColumnDef="commission">
            <th mat-header-cell *matHeaderCellDef>Commission</th>
            <td mat-cell *matCellDef="let row">
              <button mat-button (click)="editCommission(row)">{{ row.commissionRate != null ? row.commissionRate + ' %' : 'Default' }}
                <mat-icon iconPositionEnd>edit</mat-icon></button>
            </td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let row"><app-status-chip [status]="row.status" /></td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let row" class="actions">
              @if (row.status === 'PENDING') {
                <button mat-flat-button (click)="setStatus(row, 'APPROVED')"><mat-icon>check</mat-icon> Approve</button>
                <button mat-button (click)="setStatus(row, 'REJECTED')">Reject</button>
              } @else {
                <button mat-icon-button [matMenuTriggerFor]="menu" aria-label="Actions"><mat-icon>more_vert</mat-icon></button>
                <mat-menu #menu="matMenu">
                  @if (row.status !== 'APPROVED') { <button mat-menu-item (click)="setStatus(row, 'APPROVED')"><mat-icon>check_circle</mat-icon>Approve</button> }
                  @if (row.status === 'APPROVED') { <button mat-menu-item (click)="setStatus(row, 'SUSPENDED')"><mat-icon>block</mat-icon>Suspend</button> }
                </mat-menu>
              }
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
        <mat-paginator [length]="p.totalElements" [pageIndex]="p.page" [pageSize]="p.size" [hidePageSize]="true" (page)="load($event.pageIndex)" />
      }
    </section>
  `,
  styles: `
    .filters { margin-bottom: 16px; flex-wrap: wrap; }
    .small { font-size: 12px; }
    .actions { white-space: nowrap; text-align: end; }
    mat-paginator { background: transparent; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminShopsPage implements OnInit {
  readonly status = input<string>();
  private readonly api = inject(AdminApi);
  private readonly notifier = inject(Notifier);
  protected readonly filter = signal<ShopStatus | null>(null);
  protected readonly page = signal<Page<ShopAdmin> | null>(null);
  protected readonly statuses: (ShopStatus | null)[] = [null, 'PENDING', 'APPROVED', 'SUSPENDED', 'REJECTED'];
  protected readonly columns = ['shop', 'owner', 'products', 'commission', 'status', 'actions'];
  protected readonly humanize = humanize;

  ngOnInit(): void {
    this.filter.set((this.status() as ShopStatus) || null);
    this.load(0);
  }

  protected setFilter(status: ShopStatus | null): void {
    this.filter.set(status);
    this.load(0);
  }

  protected load(pageIndex: number): void {
    this.api.shops(this.filter(), pageIndex, 15).subscribe((page) => this.page.set(page));
  }

  protected setStatus(shop: ShopAdmin, status: ShopStatus): void {
    this.api.setShopStatus(shop.id, status).subscribe(() => {
      this.notifier.success(`${shop.name} is now ${humanize(status).toLowerCase()}`);
      this.load(this.page()?.page ?? 0);
    });
  }

  protected editCommission(shop: ShopAdmin): void {
    const answer = prompt(`Commission rate for ${shop.name} in % (leave empty for the platform default)`,
      shop.commissionRate?.toString() ?? '');
    if (answer === null) {
      return;
    }
    const rate = answer.trim() === '' ? null : Number(answer.replace(',', '.'));
    if (rate !== null && (Number.isNaN(rate) || rate < 0 || rate > 100)) {
      this.notifier.error('Enter a percentage between 0 and 100');
      return;
    }
    this.api.setShopCommission(shop.id, rate).subscribe(() => {
      this.notifier.success('Commission updated');
      this.load(this.page()?.page ?? 0);
    });
  }
}
