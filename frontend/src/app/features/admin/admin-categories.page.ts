import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { AdminApi } from '../../core/api/admin.api';
import { CatalogApi } from '../../core/api/catalog.api';
import { Category } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';

@Component({
  selector: 'app-admin-categories-page',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <div class="page-title"><div><h1>Categories</h1><p>{{ categories().length }} categories in two levels.</p></div></div>
    <div class="layout">
      <section class="panel">
        @for (parent of parents(); track parent.id) {
          <div class="cat">
            <span class="name"><mat-icon>folder</mat-icon> {{ parent.name }}</span>
            <span class="muted slug">/{{ parent.slug }}</span>
            <button mat-icon-button (click)="edit(parent)" aria-label="Edit"><mat-icon>edit</mat-icon></button>
            <button mat-icon-button (click)="remove(parent)" aria-label="Delete"><mat-icon>delete</mat-icon></button>
          </div>
          @for (child of childrenOf(parent.id); track child.id) {
            <div class="cat child">
              <span class="name"><mat-icon>subdirectory_arrow_right</mat-icon> {{ child.name }}</span>
              <span class="muted slug">/{{ child.slug }}</span>
              <button mat-icon-button (click)="edit(child)" aria-label="Edit"><mat-icon>edit</mat-icon></button>
              <button mat-icon-button (click)="remove(child)" aria-label="Delete"><mat-icon>delete</mat-icon></button>
            </div>
          }
        }
      </section>
      <form class="panel form" [formGroup]="form" (ngSubmit)="save()">
        <h2>{{ editing() ? 'Edit category' : 'New category' }}</h2>
        <mat-form-field><mat-label>Name</mat-label><input matInput formControlName="name" maxlength="80" /></mat-form-field>
        <mat-form-field>
          <mat-label>Parent</mat-label>
          <mat-select formControlName="parentId">
            <mat-option [value]="null">— Top level —</mat-option>
            @for (parent of parents(); track parent.id) {
              @if (parent.id !== editing()?.id) { <mat-option [value]="parent.id">{{ parent.name }}</mat-option> }
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field><mat-label>Description</mat-label><textarea matInput rows="3" formControlName="description"></textarea></mat-form-field>
        <div class="row">
          <button mat-flat-button>{{ editing() ? 'Save' : 'Create' }}</button>
          @if (editing()) { <button mat-button type="button" (click)="reset()">Cancel</button> }
        </div>
      </form>
    </div>
  `,
  styles: `
    .layout { display: grid; grid-template-columns: 1.5fr 1fr; gap: 16px; align-items: start; }
    @media (max-width: 960px) { .layout { grid-template-columns: 1fr; } }
    .cat { display: flex; align-items: center; gap: 8px; padding: 4px 0; border-bottom: 1px solid var(--mh-border); }
    .cat.child { padding-left: 24px; }
    .name { display: flex; gap: 8px; align-items: center; flex: 1; font-weight: 600; }
    .child .name { font-weight: 400; }
    .slug { font-size: 12px; }
    .form { display: flex; flex-direction: column; gap: 12px; }
    .form h2 { margin: 0; font-size: 17px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminCategoriesPage {
  private readonly admin = inject(AdminApi);
  private readonly catalog = inject(CatalogApi);
  private readonly notifier = inject(Notifier);
  protected readonly categories = signal<Category[]>([]);
  protected readonly editing = signal<Category | null>(null);
  protected readonly parents = computed(() => this.categories().filter((c) => !c.parentId));
  protected readonly form = inject(FormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    parentId: [null as number | null],
    description: [''],
  });

  constructor() {
    this.refresh();
  }

  protected childrenOf(parentId: number): Category[] {
    return this.categories().filter((c) => c.parentId === parentId);
  }

  protected edit(category: Category): void {
    this.editing.set(category);
    this.form.setValue({ name: category.name, parentId: category.parentId ?? null, description: category.description ?? '' });
  }

  protected reset(): void {
    this.editing.set(null);
    this.form.reset({ name: '', parentId: null, description: '' });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const body = { name: value.name ?? '', parentId: value.parentId, description: value.description ?? undefined };
    const editing = this.editing();
    const request = editing ? this.admin.updateCategory(editing.id, body) : this.admin.createCategory(body);
    request.subscribe(() => {
      this.notifier.success(editing ? 'Category saved' : 'Category created');
      this.reset();
      this.refresh();
    });
  }

  protected remove(category: Category): void {
    if (!confirm(`Delete the category "${category.name}"?`)) {
      return;
    }
    this.admin.deleteCategory(category.id).subscribe(() => {
      this.notifier.success('Category deleted');
      this.refresh();
    });
  }

  private refresh(): void {
    this.catalog.invalidateCategories();
    this.catalog.categories().subscribe((categories) => this.categories.set(categories));
  }
}
