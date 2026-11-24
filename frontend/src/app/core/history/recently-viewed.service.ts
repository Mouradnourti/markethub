import { Injectable, signal } from '@angular/core';
import { Product, ProductSummary } from '../models/api.models';

const STORAGE_KEY = 'mh.recentlyViewed';
const MAX_ITEMS = 12;

/** Products the visitor opened recently, kept in this browser only (a convenience, never business data). */
@Injectable({ providedIn: 'root' })
export class RecentlyViewedService {
  private readonly state = signal<ProductSummary[]>(this.read());
  readonly items = this.state.asReadonly();

  track(product: Product): void {
    const summary: ProductSummary = {
      id: product.id,
      name: product.name,
      price: product.price,
      compareAtPrice: product.compareAtPrice,
      stock: product.stock,
      avgRating: product.avgRating,
      reviewCount: product.reviewCount,
      soldCount: product.soldCount,
      imageUrl: product.images[0],
      hoverImageUrl: product.images[1],
      shopId: product.shopId,
      shopName: product.shopName,
      categoryId: product.categoryId,
      categoryName: product.categoryName,
      active: product.active,
    };
    const next = [summary, ...this.state().filter((p) => p.id !== product.id)].slice(0, MAX_ITEMS);
    this.state.set(next);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch {
      // Storage can be unavailable (private mode, quota); the list then lives for this visit only.
    }
  }

  /** Recently viewed products, excluding the one being looked at. */
  others(excludeId?: number): ProductSummary[] {
    return this.state().filter((p) => p.id !== excludeId);
  }

  private read(): ProductSummary[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      const parsed: unknown = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed) ? (parsed as ProductSummary[]).filter((p) => typeof p?.id === 'number') : [];
    } catch {
      return [];
    }
  }
}
