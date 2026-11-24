import { freeShippingProgress } from '../layout/cart-drawer';
import { listingQuality } from '../features/seller/listing-quality';
import { addBusinessDays, deliveryWindow } from './delivery';
import { discountPercent } from './ui/price';

describe('discountPercent', () => {
  it('rounds the saving against the compare-at price', () => {
    expect(discountPercent(79, 99)).toBe(20);
    expect(discountPercent(349, 399)).toBe(13);
  });

  it('is zero without a valid compare-at price', () => {
    expect(discountPercent(20, undefined)).toBe(0);
    expect(discountPercent(20, null)).toBe(0);
    expect(discountPercent(20, 20)).toBe(0);
    expect(discountPercent(20, 15)).toBe(0);
  });
});

describe('freeShippingProgress', () => {
  it('says how much is missing before free shipping', () => {
    expect(freeShippingProgress(37.6, 50)).toEqual({ remaining: 12.4, percent: 75 });
  });

  it('is complete at or above the threshold, or when free shipping is disabled', () => {
    expect(freeShippingProgress(50, 50)).toEqual({ remaining: 0, percent: 100 });
    expect(freeShippingProgress(80, 50)).toEqual({ remaining: 0, percent: 100 });
    expect(freeShippingProgress(10, 0)).toEqual({ remaining: 0, percent: 100 });
  });
});

describe('delivery estimate', () => {
  it('skips weekends', () => {
    // Friday 2026-11-27 + 1 business day = Monday 2026-11-30.
    const monday = addBusinessDays(new Date(2026, 10, 27), 1);
    expect(monday.getDay()).toBe(1);
    expect(monday.getDate()).toBe(30);
  });

  it('promises 2 to 5 business days', () => {
    const { from, to } = deliveryWindow(new Date(2026, 10, 24)); // Tuesday
    expect(from.getDate()).toBe(26); // Thursday
    expect(to.getDate()).toBe(1); // Tuesday 1 December
  });
});

describe('listingQuality', () => {
  const empty = { name: 'Shirt', tagline: '', description: '', highlights: [], specs: [], imageCount: 0 };

  it('scores an empty listing at zero with a tip for every check', () => {
    const result = listingQuality(empty);
    expect(result.score).toBe(0);
    expect(result.checks.every((c) => !c.done && c.tip.length > 0)).toBe(true);
  });

  it('reaches 100 for a complete listing and ignores blank rows', () => {
    const complete = listingQuality({
      name: 'Linen Shirt Relaxed Fit',
      tagline: 'Cool and breathable on hot days.',
      description: 'x'.repeat(150),
      highlights: ['Breathable', 'Relaxed fit', 'Machine washable'],
      specs: [
        { label: 'Material', value: 'Linen' },
        { label: 'Fit', value: 'Relaxed' },
        { label: 'Care', value: '30 °C' },
      ],
      imageCount: 2,
    });
    expect(complete.score).toBe(100);

    const blanks = listingQuality({ ...empty, highlights: ['a', ' ', ''], specs: [{ label: 'Fit', value: '' }] });
    expect(blanks.checks.find((c) => c.label.includes('benefits'))?.done).toBe(false);
    expect(blanks.checks.find((c) => c.label.includes('specifications'))?.done).toBe(false);
  });
});
