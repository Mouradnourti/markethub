import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { SellerApi } from '../../core/api/seller.api';
import { Page, ProductSummary } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { StatusChip } from '../../shared/ui/status-chip';

@Component({
  selector: 'app-seller-products-page',
  imports: [
    RouterLink,
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MoneyPipe,
    StatusChip,
    EmptyState,
  ],
  template: `
    <div class="page-title">
      <div><h1>Products</h1><p>{{ page()?.totalElements ?? 0 }} products in your catalog</p></div>
      <a mat-flat-button routerLink="/seller/products/new"><mat-icon>add</mat-icon> New product</a>
    </div>

    <section class="panel table-wrap">
      @if (page(); as p) {
        @if (p.content.length) {
          <table mat-table [dataSource]="p.content">
            <ng-container matColumnDef="product">
              <th mat-header-cell *matHeaderCellDef>Product</th>
              <td mat-cell *matCellDef="let row">
                <div class="product">
                  @if (row.imageUrl) { <img [src]="row.imageUrl" alt="" /> }
                  <div><strong>{{ row.name }}</strong><span class="muted">{{ row.categoryName }}</span></div>
                </div>
              </td>
            </ng-container>
            <ng-container matColumnDef="price">
              <th mat-header-cell *matHeaderCellDef>Price</th>
              <td mat-cell *matCellDef="let row">{{ row.price | money }}</td>
            </ng-container>
            <ng-container matColumnDef="stock">
              <th mat-header-cell *matHeaderCellDef>Stock</th>
              <td mat-cell *matCellDef="let row">
                @if (editing() === row.id) {
                  <span class="stock-edit">
                    <input type="number" min="0" [(ngModel)]="stockValue" (keyup.enter)="saveStock(row)" aria-label="Stock" />
                    <button mat-icon-button (click)="saveStock(row)" aria-label="Save"><mat-icon>check</mat-icon></button>
                  </span>
                } @else {
                  <button class="stock" [class.low]="row.stock < 5" (click)="editStock(row)" matTooltip="Click to edit">
                    {{ row.stock }} <mat-icon>edit</mat-icon>
                  </button>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="sold">
              <th mat-header-cell *matHeaderCellDef>Sold</th>
              <td mat-cell *matCellDef="let row">{{ row.soldCount }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let row">
                <app-status-chip [status]="row.active ? 'APPROVED' : 'CANCELLED'" [text]="row.active ? 'Online' : 'Hidden'" />
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let row" class="actions">
                <a mat-icon-button [routerLink]="['/seller/products', row.id]" aria-label="Edit"><mat-icon>edit_square</mat-icon></a>
                <a mat-icon-button [routerLink]="['/products', row.id]" aria-label="View in store"><mat-icon>open_in_new</mat-icon></a>
                <button mat-icon-button (click)="remove(row)" aria-label="Delete"><mat-icon>delete</mat-icon></button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
          <mat-paginator [length]="p.totalElements" [pageIndex]="p.page" [pageSize]="p.size" [hidePageSize]="true"
                         (page)="load($event.pageIndex)" />
        } @else {
          <app-empty-state icon="inventory_2" title="No products yet" message="Add your first product to start selling.">
            <a mat-flat-button routerLink="/seller/products/new">Add a product</a>
          </app-empty-state>
        }
      }
    </section>
  `,
  styles: `
    .product { display: flex; gap: 12px; align-items: center; padding: 6px 0; }
    .product img { width: 44px; height: 44px; border-radius: 10px; object-fit: cover; }
    .product div { display: flex; flex-direction: column; }
    .product span { font-size: 12px; }
    .stock { display: inline-flex; align-items: center; gap: 4px; border: 0; background: var(--mh-surface-2); border-radius: 8px;
      padding: 2px 8px; font: inherit; font-weight: 600; cursor: pointer; }
    .stock mat-icon { width: 14px; height: 14px; font-size: 14px; opacity: .5; }
    .stock.low { background: var(--mh-warning-bg); color: var(--mh-warning); }
    .stock-edit { display: inline-flex; align-items: center; }
    .stock-edit input { width: 70px; padding: 4px 6px; border: 1px solid var(--mh-border); border-radius: 6px; font: inherit; }
    .actions { white-space: nowrap; text-align: right; }
    mat-paginator { background: transparent; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SellerProductsPage {
  private readonly api = inject(SellerApi);
  private readonly notifier = inject(Notifier);
  protected readonly page = signal<Page<ProductSummary> | null>(null);
  protected readonly editing = signal<number | null>(null);
  protected stockValue = 0;
  protected readonly columns = ['product', 'price', 'stock', 'sold', 'status', 'actions'];

  constructor() {
    this.load(0);
  }

  protected load(pageIndex: number): void {
    this.api.products(pageIndex, 15).subscribe((page) => this.page.set(page));
  }

  protected editStock(row: ProductSummary): void {
    this.stockValue = row.stock;
    this.editing.set(row.id);
  }

  protected saveStock(row: ProductSummary): void {
    this.api.updateStock(row.id, Math.max(0, Math.floor(this.stockValue))).subscribe(() => {
      this.notifier.success('Stock updated');
      this.editing.set(null);
      this.load(this.page()?.page ?? 0);
    });
  }

  protected remove(row: ProductSummary): void {
    if (!confirm(`Delete "${row.name}"? Products that were already ordered can only be hidden.`)) {
      return;
    }
    this.api.deleteProduct(row.id).subscribe(() => {
      this.notifier.success('Product deleted');
      this.load(this.page()?.page ?? 0);
    });
  }
}
