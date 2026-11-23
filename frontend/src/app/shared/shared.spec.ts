import { TestBed } from '@angular/core/testing';
import { ConfigService } from '../core/config/config.service';
import { MoneyPipe } from './pipes/money.pipe';
import { SELLER_NEXT_STATUSES, humanize, statusTone } from './status';
import { RiskBadge } from './ui/risk-badge';

describe('MoneyPipe', () => {
  it('formats amounts in the platform currency', () => {
    TestBed.configureTestingModule({ providers: [{ provide: ConfigService, useValue: { currencyCode: 'EUR' } }] });
    const pipe = TestBed.runInInjectionContext(() => new MoneyPipe());
    expect(pipe.transform(1234.5)).toBe('€1,234.50');
    expect(pipe.transform(null)).toBe('');
  });
});

describe('status helpers', () => {
  it('maps statuses to labels and tones', () => {
    expect(humanize('PENDING')).toBe('Placed');
    expect(humanize('AWAITING_PAYMENT')).toBe('Awaiting payment');
    expect(statusTone('DELIVERED')).toBe('success');
    expect(statusTone('RETURNED')).toBe('danger');
  });

  it('mirrors the backend seller transitions', () => {
    expect(SELLER_NEXT_STATUSES.SHIPPED).toEqual(['DELIVERED', 'RETURNED']);
    expect(SELLER_NEXT_STATUSES.DELIVERED).toBeUndefined();
  });
});

describe('RiskBadge', () => {
  function render(score: number | null): HTMLElement {
    const fixture = TestBed.createComponent(RiskBadge);
    fixture.componentRef.setInput('score', score);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the risk level from the AI score', () => {
    expect(render(0.52).textContent).toContain('High risk · 52%');
    expect(render(0.2).textContent).toContain('Medium risk');
    expect(render(0.05).textContent).toContain('Low risk');
  });

  it('renders nothing without a score', () => {
    expect(render(null).textContent?.trim()).toBe('');
  });
});
