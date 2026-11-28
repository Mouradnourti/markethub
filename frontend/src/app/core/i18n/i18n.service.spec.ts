import { TestBed } from '@angular/core/testing';
import { AR } from './ar';
import { FR } from './fr';
import { I18nService, storedLanguage } from './i18n.service';

describe('I18nService', () => {
  afterEach(() => localStorage.clear());

  it('keeps English as the source and fills placeholders', () => {
    localStorage.setItem('mh.lang', 'en');
    const i18n = TestBed.inject(I18nService);
    expect(i18n.t('Hi, {name}', { name: 'Sara' })).toBe('Hi, Sara');
    expect(i18n.t('A sentence nobody translated')).toBe('A sentence nobody translated');
    expect(document.documentElement.dir).toBe('ltr');
  });

  it('switches the document to right-to-left in Arabic', async () => {
    localStorage.setItem('mh.lang', 'ar');
    const i18n = TestBed.inject(I18nService);
    await i18n.load();
    expect(i18n.rtl).toBe(true);
    expect(document.documentElement.dir).toBe('rtl');
    expect(document.documentElement.lang).toBe('ar');
    expect(i18n.t('Add to cart')).toBe(AR['Add to cart']);
    expect(i18n.intlLocale).toContain('latn');
  });

  it('remembers the choice and ignores unknown values', () => {
    localStorage.setItem('mh.lang', 'fr');
    expect(storedLanguage()).toBe('fr');
    localStorage.setItem('mh.lang', 'xx');
    expect(['en', 'fr', 'ar']).toContain(storedLanguage());
  });

  it('has French and Arabic for the same keys, with the same placeholders', () => {
    const placeholders = (s: string) => (s.match(/\{\w+\}/g) ?? []).sort().join(',');
    expect(Object.keys(AR).sort()).toEqual(Object.keys(FR).sort());
    for (const [key, value] of Object.entries(FR)) {
      expect(placeholders(value), key).toBe(placeholders(key));
      expect(placeholders(AR[key]), key).toBe(placeholders(key));
    }
  });
});
