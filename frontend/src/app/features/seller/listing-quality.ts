import { ProductSpec } from '../../core/models/api.models';

export interface ListingInput {
  name: string;
  tagline: string;
  description: string;
  highlights: string[];
  specs: ProductSpec[];
  imageCount: number;
}

export interface QualityCheck {
  label: string;
  tip: string;
  weight: number;
  done: boolean;
}

/**
 * Scores how complete a product page is (0–100). Complete pages answer shoppers' questions up front, which is
 * what turns visits into orders; each unmet check comes with a concrete tip.
 */
export function listingQuality(listing: ListingInput): { score: number; checks: QualityCheck[] } {
  const filledSpecs = listing.specs.filter((s) => s.label.trim() && s.value.trim()).length;
  const benefits = listing.highlights.filter((h) => h.trim()).length;
  const checks: QualityCheck[] = [
    {
      label: 'Descriptive name',
      tip: 'Include the product type and a key attribute, e.g. "Linen Shirt Relaxed Fit".',
      weight: 10,
      done: listing.name.trim().length >= 15,
    },
    {
      label: 'Tagline',
      tip: 'Add a one-line pitch that says why someone should buy it.',
      weight: 15,
      done: listing.tagline.trim().length >= 10,
    },
    {
      label: 'Detailed description',
      tip: 'Write at least 150 characters about what it is, who it is for and how it is used.',
      weight: 20,
      done: listing.description.trim().length >= 150,
    },
    {
      label: 'At least 3 key benefits',
      tip: 'Short bullet points are what shoppers read first.',
      weight: 20,
      done: benefits >= 3,
    },
    {
      label: 'At least 3 specifications',
      tip: 'Size, material, capacity… specs prevent returns and questions.',
      weight: 15,
      done: filledSpecs >= 3,
    },
    {
      label: 'At least 2 images',
      tip: 'Show the product from several angles.',
      weight: 20,
      done: listing.imageCount >= 2,
    },
  ];
  const score = checks.filter((c) => c.done).reduce((sum, c) => sum + c.weight, 0);
  return { score, checks };
}
