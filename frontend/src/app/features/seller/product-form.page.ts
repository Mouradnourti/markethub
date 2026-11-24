import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Router, RouterLink } from '@angular/router';
import { startWith } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { SellerApi } from '../../core/api/seller.api';
import { ConfigService } from '../../core/config/config.service';
import { CopyDraft, CopyTone, Product, ProductRequest, ProductSpec } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';
import { discountPercent } from '../../shared/ui/price';
import { listingQuality } from './listing-quality';

const MAX_HIGHLIGHTS = 8;
const MAX_SPECS = 20;

type SpecGroup = FormGroup<{ label: FormControl<string>; value: FormControl<string> }>;

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
    MatButtonToggleModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './product-form.page.html',
  styleUrl: './product-form.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductFormPage implements OnInit {
  readonly id = input<string>();
  private readonly api = inject(SellerApi);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly categories = toSignal(inject(CatalogApi).categories(), { initialValue: [] });

  protected readonly product = signal<Product | null>(null);
  protected readonly saving = signal(false);
  protected readonly uploading = signal(false);
  protected readonly writing = signal(false);
  protected readonly draftVariant = signal(0);
  protected readonly lastDraft = signal<CopyDraft | null>(null);
  protected readonly isEdit = computed(() => !!this.id());
  protected readonly currency = inject(ConfigService).currencyCode;
  protected readonly maxHighlights = MAX_HIGHLIGHTS;
  protected readonly maxSpecs = MAX_SPECS;
  protected readonly categoryGroups = computed(() =>
    this.categories()
      .filter((c) => !c.parentId)
      .map((parent) => ({ parent, children: this.categories().filter((c) => c.parentId === parent.id) })),
  );

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    categoryId: [0, [Validators.required, Validators.min(1)]],
    price: [0, [Validators.required, Validators.min(0.01)]],
    compareAtPrice: [null as number | null],
    stock: [0, [Validators.required, Validators.min(0)]],
    active: [true],
    tagline: ['', Validators.maxLength(200)],
    description: ['', Validators.maxLength(5000)],
    highlights: this.fb.array<FormControl<string>>([]),
    specs: this.fb.array<SpecGroup>([]),
  });

  /** Inputs of the AI writer (not part of the product). */
  protected readonly writer = this.fb.group({
    keywords: [''],
    tone: ['friendly' as CopyTone],
  });

  private readonly formValue = toSignal(this.form.valueChanges.pipe(startWith(null)));
  protected readonly quality = computed(() => {
    this.formValue();
    const value = this.form.getRawValue();
    return listingQuality({
      name: value.name,
      tagline: value.tagline,
      description: value.description,
      highlights: value.highlights,
      specs: value.specs,
      imageCount: this.product()?.images.length ?? 0,
    });
  });
  protected readonly discount = computed(() => {
    this.formValue();
    const { price, compareAtPrice } = this.form.getRawValue();
    return discountPercent(Number(price), compareAtPrice ? Number(compareAtPrice) : null);
  });

  ngOnInit(): void {
    const id = this.id();
    if (id) {
      this.api.product(Number(id)).subscribe((product) => {
        this.product.set(product);
        this.form.patchValue({ ...product, compareAtPrice: product.compareAtPrice ?? null, tagline: product.tagline ?? '' });
        this.setHighlights(product.highlights);
        this.setSpecs(product.specs);
      });
    }
  }

  protected get highlights() {
    return this.form.controls.highlights;
  }

  protected get specs() {
    return this.form.controls.specs;
  }

  protected addHighlight(text = ''): void {
    if (this.highlights.length < MAX_HIGHLIGHTS) {
      this.highlights.push(this.fb.control(text, [Validators.required, Validators.maxLength(160)]));
    }
  }

  protected addSpec(spec: ProductSpec = { label: '', value: '' }): void {
    if (this.specs.length < MAX_SPECS) {
      this.specs.push(
        this.fb.group({
          label: [spec.label, [Validators.required, Validators.maxLength(60)]],
          value: [spec.value, [Validators.required, Validators.maxLength(200)]],
        }),
      );
    }
  }

  /** Asks the AI writer for a draft and fills the page content; the seller reviews it before saving. */
  protected write(nextVersion = false): void {
    const { name, categoryId } = this.form.getRawValue();
    if (name.trim().length < 2) {
      this.form.controls.name.markAsTouched();
      this.notifier.error('Enter the product name first: the writer starts from it.');
      return;
    }
    if (nextVersion) {
      this.draftVariant.update((v) => v + 1);
    }
    const keywords = this.writer.getRawValue().keywords
      .split(/[,\n]/)
      .map((k) => k.trim())
      .filter(Boolean)
      .slice(0, 12);
    this.writing.set(true);
    this.api
      .describe({
        name: name.trim(),
        categoryId: categoryId || undefined,
        keywords,
        tone: this.writer.getRawValue().tone,
        variant: this.draftVariant(),
      })
      .subscribe({
        next: (draft) => {
          this.writing.set(false);
          this.lastDraft.set(draft);
          this.form.patchValue({ tagline: draft.tagline, description: draft.description });
          this.setHighlights(draft.highlights);
          const filled = this.specs.getRawValue().filter((s) => s.label.trim() && s.value.trim());
          const known = new Set(filled.map((s) => s.label.toLowerCase()));
          const extracted = draft.specs.filter((s) => !known.has(s.label.toLowerCase()));
          extracted.forEach((s) => known.add(s.label.toLowerCase()));
          const suggested = draft.suggestedSpecLabels
            .filter((label) => !known.has(label.toLowerCase()))
            .slice(0, 3)
            .map((label) => ({ label, value: '' }));
          this.setSpecs([...filled, ...extracted, ...suggested]);
          this.form.markAsDirty();
          this.notifier.success('Draft ready. Review it, fill in the empty specs, then save.');
        },
        error: () => this.writing.set(false),
      });
  }

  protected save(): void {
    // Spec rows the seller left empty (e.g. suggested labels) are dropped rather than blocking the save.
    for (let i = this.specs.length - 1; i >= 0; i--) {
      const { label, value } = this.specs.at(i).getRawValue();
      if (!label.trim() && !value.trim()) {
        this.specs.removeAt(i);
      }
    }
    for (let i = this.highlights.length - 1; i >= 0; i--) {
      if (!this.highlights.at(i).value.trim()) {
        this.highlights.removeAt(i);
      }
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notifier.error('Please complete the highlighted fields.');
      return;
    }
    const value = this.form.getRawValue();
    const compareAt = value.compareAtPrice ? Number(value.compareAtPrice) : null;
    if (compareAt !== null && compareAt <= Number(value.price)) {
      this.notifier.error('The "compare-at" price must be higher than the price, or empty.');
      return;
    }
    this.saving.set(true);
    const body: ProductRequest = { ...value, compareAtPrice: compareAt, imageUrls: this.product()?.images };
    const request = this.product() ? this.api.updateProduct(this.product()!.id, body) : this.api.createProduct(body);
    request.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.notifier.success(this.product() ? 'Product saved' : 'Product created: you can now add images');
        if (!this.product()) {
          void this.router.navigate(['/seller/products', saved.id], { replaceUrl: true });
        }
        this.product.set(saved);
        this.form.markAsPristine();
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

  private setHighlights(items: string[]): void {
    this.highlights.clear();
    items.slice(0, MAX_HIGHLIGHTS).forEach((text) => this.addHighlight(text));
  }

  private setSpecs(items: ProductSpec[]): void {
    this.specs.clear();
    items.slice(0, MAX_SPECS).forEach((spec) => this.addSpec(spec));
  }
}
