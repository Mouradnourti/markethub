import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type Lang = 'en' | 'fr' | 'ar';

export interface LanguageOption {
  code: Lang;
  label: string;
  dir: 'ltr' | 'rtl';
}

export const LANGUAGES: LanguageOption[] = [
  { code: 'en', label: 'English', dir: 'ltr' },
  { code: 'fr', label: 'Français', dir: 'ltr' },
  { code: 'ar', label: 'العربية', dir: 'rtl' },
];

const STORAGE_KEY = 'mh.lang';
/** Each language is its own lazy chunk: English visitors never download the French or Arabic strings. */
const LOADERS: Record<Lang, () => Promise<Record<string, string>>> = {
  en: () => Promise.resolve({}),
  fr: () => import('./fr').then((m) => m.FR),
  ar: () => import('./ar').then((m) => m.AR),
};
/** Intl locales for numbers and prices; Arabic keeps Latin digits, as most Moroccan shops do. */
const INTL_LOCALES: Record<Lang, string> = { en: 'en-US', fr: 'fr-FR', ar: 'ar-MA-u-nu-latn' };

export function storedLanguage(): Lang {
  try {
    const saved = localStorage.getItem(STORAGE_KEY) as Lang | null;
    if (saved && saved in LOADERS) {
      return saved;
    }
  } catch {
    // Storage can be blocked (private mode): fall back to the browser language.
  }
  const browser = (typeof navigator !== 'undefined' ? navigator.language : 'en').slice(0, 2);
  return browser === 'fr' || browser === 'ar' ? browser : 'en';
}

/**
 * Interface translations. English source strings are the keys, so a missing translation shows English.
 * Product content written by sellers stays in its own language. Switching language reloads the app so that
 * Angular Material, dates and number formats all pick up the new locale and direction.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly document = inject(DOCUMENT);
  readonly lang = signal<Lang>(storedLanguage());
  private dictionary: Record<string, string> = {};

  constructor() {
    const option = this.option;
    this.document.documentElement.lang = option.code;
    this.document.documentElement.dir = option.dir;
  }

  /** Loads the strings of the current language; called once by the app initializer. */
  async load(): Promise<void> {
    try {
      this.dictionary = await LOADERS[this.lang()]();
    } catch {
      this.dictionary = {};
    }
  }

  get option(): LanguageOption {
    return LANGUAGES.find((l) => l.code === this.lang()) ?? LANGUAGES[0];
  }

  get rtl(): boolean {
    return this.option.dir === 'rtl';
  }

  get intlLocale(): string {
    return INTL_LOCALES[this.lang()];
  }

  /** Translates and fills {placeholders}: t('Hi, {name}', { name: 'Sara' }). */
  t(key: string, params?: Record<string, string | number | null | undefined>): string {
    let text = this.dictionary[key] ?? key;
    if (params) {
      for (const [name, value] of Object.entries(params)) {
        text = text.replaceAll(`{${name}}`, value === null || value === undefined ? '' : String(value));
      }
    }
    return text;
  }

  use(lang: Lang): void {
    if (lang === this.lang()) {
      return;
    }
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // Without storage the choice lasts for this page only.
    }
    this.document.defaultView?.location.reload();
  }
}
