import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Router, RouterLink } from '@angular/router';
import { CatalogApi } from '../../core/api/catalog.api';
import { SellerApi } from '../../core/api/seller.api';
import { Product } from '../../core/models/api.models';
import { ConfigService } from '../../core/config/config.service';
import { Notifier } from '../../core/ui/notifier.service';

@Component({
  selector: 'app-product-form-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  template: `
    <div class="page-title">
      <div>
        <a routerLink="/seller/products" class="back"><mat-icon>arrow_back</mat-icon> Products</a>
        <h1>{{ isEdit() ? 'Edit product' : 'New product' }}</h1>
      </div>
    </div>

    <form class="layout" [formGroup]="form" (ngSubmit)="save()">
      <section class="panel fields">
        <mat-form-field class="span-2">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" maxlength="200" />
          @if (form.controls.name.hasError('required')) { <mat-error>The name is required</mat-error> }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Category</mat-label>
          <mat-select formControlName="categoryId">
            @for (group of categoryGroups(); track group.parent.id) {
              <mat-optgroup [label]="group.parent.name">
                @if (!group.children.length) { <mat-option [value]="group.parent.id">{{ group.parent.name }}</mat-option> }
                @for (child of group.children; track child.id) {
                  <mat-option [value]="child.id">{{ child.name }}</mat-option>
                }
              </mat-optgroup>
            }
          </mat-select>
        </mat-form-field>
        <mat-slide-toggle formControlName="active" class="toggle">Visible in the store</mat-slide-toggle>
        <mat-form-field>
          <mat-label>Price</mat-label>
          <input matInput type="number" step="0.01" min="0.01" formControlName="price" />
          <span matTextSuffix>{{ currency }}</span>
          @if (form.controls.price.invalid) { <mat-error>Enter a price above 0</mat-error> }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Stock</mat-label>
          <input matInput type="number" min="0" step="1" formControlName="stock" />
        </mat-form-field>
        <mat-form-field class="span-2">
          <mat-label>Description</mat-label>
          <textarea matInput rows="6" formControlName="description" maxlength="5000"></textarea>
        </mat-form-field>
        <div class="span-2 row">
          <button mat-flat-button [disabled]="saving()"><mat-icon>save</mat-icon> {{ isEdit() ? 'Save changes' : 'Create product' }}</button>
          <a mat-button routerLink="/seller/products">Cancel</a>
        </div>
      </section>

      <section class="panel images">
        <h2>Images</h2>
        @if (product(); as p) {
          <div class="gallery">
            @for (src of p.images; track src) {
              <img [src]="src" alt="" />
            }
          </div>
          <label class="upload">
            <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" (change)="upload($event)" hidden />
            <mat-icon>cloud_upload</mat-icon>
            <span>Upload an image (JPEG, PNG, WebP · max 5 MB)</span>
          </label>
          @if (uploading()) { <mat-progress-bar mode="indeterminate" /> }
        } @else {
          <p class="muted">Save the product first, then add images.</p>
        }
      </section>
    </form>
  `,
  styles: `
    .back { display: inline-flex; gap: 4px; align-items: center; font-size: 14px; text-decoration: none; }
    .layout { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; align-items: start; }
    @media (max-width: 960px) { .layout { grid-template-columns: 1fr; } }
    .fields { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
    .span-2 { grid-column: span 2; }
    .toggle { align-self: center; }
    .images h2 { margin-top: 0; font-size: 17px; }
    .gallery { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 12px; }
    .gallery img { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: 10px; border: 1px solid var(--mh-border); }
    .upload { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 20px; border: 2px dashed var(--mh-border);
      border-radius: 14px; color: var(--mh-text-muted); text-align: center; font-size: 13px; cursor: pointer; }
    .upload:hover { border-color: var(--mh-accent); color: var(--mh-accent); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductFormPage implements OnInit {
  readonly id = input<string>();
  private readonly api = inject(SellerApi);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);
  private readonly categories = toSignal(inject(CatalogApi).categories(), { initialValue: [] });

  protected readonly product = signal<Product | null>(null);
  protected readonly saving = signal(false);
  protected readonly uploading = signal(false);
  protected readonly isEdit = computed(() => !!this.id());
  protected readonly currency = inject(ConfigService).currencyCode;
  protected readonly categoryGroups = computed(() =>
    this.categories()
      .filter((c) => !c.parentId)
      .map((parent) => ({ parent, children: this.categories().filter((c) => c.parentId === parent.id) })),
  );

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    categoryId: [0, [Validators.required, Validators.min(1)]],
    price: [0, [Validators.required, Validators.min(0.01)]],
    stock: [0, [Validators.required, Validators.min(0)]],
    active: [true],
    description: [''],
  });

  ngOnInit(): void {
    const id = this.id();
    if (id) {
      this.api.product(Number(id)).subscribe((product) => {
        this.product.set(product);
        this.form.patchValue(product);
      });
    }
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notifier.error('Please complete the required fields.');
      return;
    }
    this.saving.set(true);
    const body = { ...this.form.getRawValue(), imageUrls: this.product()?.images };
    const request = this.product()
      ? this.api.updateProduct(this.product()!.id, body)
      : this.api.createProduct(body);
    request.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.notifier.success(this.product() ? 'Product saved' : 'Product created — you can now add images');
        if (!this.product()) {
          void this.router.navigate(['/seller/products', saved.id], { replaceUrl: true });
        }
        this.product.set(saved);
      },
      error: () => this.saving.set(false),
    });
  }

  protected upload(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    const product = this.product();
    if (!file || !product) {
      return;
    }
    this.uploading.set(true);
    this.api.uploadImage(product.id, file).subscribe({
      next: (updated) => {
        this.product.set(updated);
        this.uploading.set(false);
        this.notifier.success('Image uploaded');
      },
      error: () => this.uploading.set(false),
    });
  }
}
