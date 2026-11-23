import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { Router, RouterLink } from '@angular/router';
import { Stripe, StripeElements, loadStripe } from '@stripe/stripe-js';
import { OrderApi } from '../../core/api/order.api';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
import { CheckoutResponse, PaymentMethod } from '../../core/models/api.models';
import { Notifier } from '../../core/ui/notifier.service';
import { MoneyPipe } from '../../shared/pipes/money.pipe';

type Step = 'details' | 'payment';

@Component({
  selector: 'app-checkout-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MoneyPipe,
  ],
  templateUrl: './checkout.page.html',
  styleUrl: './checkout.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutPage {
  protected readonly cart = inject(CartService);
  private readonly orders = inject(OrderApi);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);
  private readonly user = inject(AuthService).user();

  protected readonly step = signal<Step>('details');
  protected readonly placing = signal(false);
  protected readonly paying = signal(false);
  protected readonly checkout = signal<CheckoutResponse | null>(null);
  protected readonly paymentError = signal<string | null>(null);
  private readonly stripeHost = viewChild<ElementRef<HTMLDivElement>>('stripeHost');
  private stripe: Stripe | null = null;
  private elements: StripeElements | null = null;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    fullName: [this.user ? `${this.user.firstName} ${this.user.lastName}` : '', [Validators.required, Validators.maxLength(160)]],
    phone: [this.user?.phone ?? '', [Validators.required, Validators.pattern(/^[+0-9 ()-]{6,30}$/)]],
    addressLine: ['', [Validators.required, Validators.maxLength(255)]],
    city: ['', [Validators.required, Validators.maxLength(100)]],
    postalCode: ['', [Validators.required, Validators.maxLength(20)]],
    country: ['France', [Validators.required, Validators.maxLength(80)]],
    paymentMethod: ['COD' as PaymentMethod, Validators.required],
  });

  constructor() {
    this.cart.refresh().subscribe({
      next: (cart) => {
        if (!cart.items.length && this.step() === 'details') {
          void this.router.navigateByUrl('/cart');
        }
      },
      error: () => undefined,
    });
  }

  protected placeOrder(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { paymentMethod, ...address } = this.form.getRawValue();
    this.placing.set(true);
    this.orders.checkout(paymentMethod, address).subscribe({
      next: (response) => {
        this.cart.reset();
        this.checkout.set(response);
        this.placing.set(false);
        if (!response.payment) {
          this.finish(response.order.id, 'Order placed! You will pay on delivery.');
          return;
        }
        this.step.set('payment');
        if (response.payment.provider === 'STRIPE') {
          void this.mountStripe(response);
        }
      },
      error: () => this.placing.set(false),
    });
  }

  /** Simulated provider (no Stripe key configured): no card data is collected. */
  protected paySimulated(decline: boolean): void {
    this.confirmOnBackend(decline);
  }

  protected async payWithStripe(): Promise<void> {
    if (!this.stripe || !this.elements) {
      return;
    }
    this.paying.set(true);
    this.paymentError.set(null);
    const { error } = await this.stripe.confirmPayment({ elements: this.elements, redirect: 'if_required' });
    if (error) {
      this.paying.set(false);
      this.paymentError.set(error.message ?? 'The payment was declined.');
      return;
    }
    this.confirmOnBackend(false);
  }

  private confirmOnBackend(decline: boolean): void {
    const order = this.checkout()?.order;
    if (!order) {
      return;
    }
    this.paying.set(true);
    this.orders.confirmPayment(order.id, decline).subscribe({
      next: ({ paymentStatus }) => {
        if (paymentStatus === 'PAID') {
          this.finish(order.id, 'Payment accepted, thank you for your order!');
        } else if (paymentStatus === 'FAILED') {
          this.notifier.error('The payment was declined. Your order was cancelled and the items released.');
          void this.router.navigate(['/orders', order.id]);
        } else {
          this.finish(order.id, 'Payment is being processed.');
        }
      },
      error: () => this.paying.set(false),
    });
  }

  private async mountStripe(response: CheckoutResponse): Promise<void> {
    const payment = response.payment!;
    this.stripe = await loadStripe(payment.publishableKey!);
    if (!this.stripe) {
      this.paymentError.set('Stripe could not be loaded. Check your connection.');
      return;
    }
    this.elements = this.stripe.elements({ clientSecret: payment.clientSecret, appearance: { theme: 'stripe' } });
    const host = this.stripeHost()?.nativeElement;
    if (host) {
      this.elements.create('payment').mount(host);
    }
  }

  private finish(orderId: number, message: string): void {
    this.notifier.success(message);
    void this.router.navigate(['/orders', orderId], { queryParams: { placed: 1 } });
  }
}
