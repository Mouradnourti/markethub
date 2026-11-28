<?php

namespace Tests\Feature;

use App\Mail\CartReminderMail;
use App\Mail\ProductAlertMail;
use Illuminate\Support\Facades\Mail;
use Tests\TestCase;

class CustomerMessageTest extends TestCase
{
    private const HEADERS = ['X-Internal-Key' => 'test-internal-key', 'Accept' => 'application/json'];

    public function test_price_drop_alert_shows_old_and_new_price(): void
    {
        Mail::fake();

        $this->postJson('/api/notifications/product-alert', $this->alert('PRICE_DROP', 79.0, 99.0), self::HEADERS)
            ->assertStatus(202);

        Mail::assertSent(ProductAlertMail::class, function (ProductAlertMail $mail) {
            $html = $mail->render();

            return $mail->hasTo('sara@example.test')
                && $mail->envelope()->subject === 'Price drop: Nova Headphones is now €79.00'
                && str_contains($html, '€99.00')
                && str_contains($html, 'http://localhost:4200/products/12');
        });
    }

    public function test_back_in_stock_alert_and_validation(): void
    {
        Mail::fake();

        $this->postJson('/api/notifications/product-alert', $this->alert('BACK_IN_STOCK', 99.0, null), self::HEADERS)
            ->assertStatus(202);
        Mail::assertSent(ProductAlertMail::class,
            fn (ProductAlertMail $mail) => $mail->envelope()->subject === 'Nova Headphones is back in stock');

        $this->postJson('/api/notifications/product-alert', $this->alert('PRICE_DROP', 79.0, null), self::HEADERS)
            ->assertUnprocessable()->assertJsonValidationErrors('product.previousPrice');
        $this->postJson('/api/notifications/product-alert', $this->alert('FLASH_SALE', 79.0, null), self::HEADERS)
            ->assertUnprocessable()->assertJsonValidationErrors('type');
    }

    public function test_cart_reminder_lists_the_cart(): void
    {
        Mail::fake();
        $payload = [
            'recipient' => ['name' => 'Sara Customer', 'email' => 'sara@example.test'],
            'cart' => [
                'currency' => 'eur', 'subtotal' => 158.0, 'url' => 'http://localhost:4200/cart',
                'items' => [
                    ['name' => 'Nova Headphones', 'quantity' => 2, 'unitPrice' => 79.0, 'lineTotal' => 158.0],
                ],
            ],
        ];

        $this->postJson('/api/notifications/cart-reminder', $payload, self::HEADERS)->assertStatus(202);

        Mail::assertSent(CartReminderMail::class, function (CartReminderMail $mail) {
            $html = $mail->render();

            return $mail->envelope()->subject === 'You left something in your cart'
                && str_contains($html, '2 × Nova Headphones')
                && str_contains($html, '€158.00');
        });
        $this->postJson('/api/notifications/cart-reminder', ['recipient' => $payload['recipient']], self::HEADERS)
            ->assertUnprocessable();
    }

    private function alert(string $type, float $price, ?float $previous): array
    {
        return [
            'type' => $type,
            'recipient' => ['name' => 'Sara Customer', 'email' => 'sara@example.test'],
            'product' => [
                'name' => 'Nova Headphones', 'shopName' => 'SoundWave Audio', 'currency' => 'eur',
                'price' => $price, 'previousPrice' => $previous, 'url' => 'http://localhost:4200/products/12',
            ],
        ];
    }
}
