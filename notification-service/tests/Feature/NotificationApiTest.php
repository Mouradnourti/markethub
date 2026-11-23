<?php

namespace Tests\Feature;

use App\Mail\OrderEventMail;
use Illuminate\Support\Facades\Mail;
use Tests\TestCase;

class NotificationApiTest extends TestCase
{
    private const HEADERS = ['X-Internal-Key' => 'test-internal-key', 'Accept' => 'application/json'];

    public function test_health_is_public(): void
    {
        $this->getJson('/api/health')->assertOk()->assertJson(['status' => 'UP']);
    }

    public function test_calls_without_the_internal_key_are_rejected(): void
    {
        $this->postJson('/api/invoices', [])->assertUnauthorized();
        $this->postJson('/api/notifications/order-event', [], ['X-Internal-Key' => 'wrong'])->assertUnauthorized();
    }

    public function test_order_confirmation_is_sent_with_the_pdf_invoice(): void
    {
        Mail::fake();

        $this->postJson('/api/notifications/order-event', $this->eventPayload('ORDER_PLACED'), self::HEADERS)
            ->assertStatus(202);

        Mail::assertSent(OrderEventMail::class, function (OrderEventMail $mail) {
            $attachments = $mail->attachments();

            return $mail->hasTo('sara@example.test')
                && $mail->envelope()->subject === 'Your order MH-261123-ABC123 is confirmed'
                && count($attachments) === 1
                && str_starts_with($mail->invoicePdf, '%PDF');
        });
    }

    public function test_shipping_notice_names_the_parcel_and_has_no_attachment(): void
    {
        Mail::fake();
        $payload = $this->eventPayload('ORDER_SHIPPED');
        $payload['parcelIndex'] = 0;
        $payload['order']['shopOrders'][0]['status'] = 'SHIPPED';
        $payload['order']['shopOrders'][0]['trackingNumber'] = 'TRK-42';

        $this->postJson('/api/notifications/order-event', $payload, self::HEADERS)->assertStatus(202);

        Mail::assertSent(OrderEventMail::class, function (OrderEventMail $mail) {
            $html = $mail->render();

            return $mail->envelope()->subject === 'Your parcel from SoundWave Audio is on its way'
                && $mail->attachments() === []
                && str_contains($html, 'TRK-42')
                && str_contains($html, '€129.00');
        });
    }

    public function test_invalid_payload_returns_field_errors(): void
    {
        $payload = $this->eventPayload('ORDER_EXPLODED');
        unset($payload['order']['shopOrders']);

        $this->postJson('/api/notifications/order-event', $payload, self::HEADERS)
            ->assertStatus(422)
            ->assertJsonValidationErrors(['event', 'order.shopOrders']);
    }

    public function test_invoice_endpoint_returns_a_pdf(): void
    {
        $payload = ['customer' => ['name' => 'Sara Martin', 'email' => 'sara@example.test'], 'order' => $this->order()];

        $response = $this->postJson('/api/invoices', $payload, self::HEADERS);

        $response->assertOk()->assertHeader('Content-Type', 'application/pdf');
        $this->assertStringStartsWith('%PDF', $response->getContent());
        $this->assertStringContainsString('invoice-MH-261123-ABC123.pdf', $response->headers->get('Content-Disposition'));
    }

    private function eventPayload(string $event): array
    {
        return [
            'event' => $event,
            'recipient' => ['name' => 'Sara Martin', 'email' => 'sara@example.test'],
            'order' => $this->order(),
        ];
    }

    private function order(): array
    {
        return [
            'reference' => 'MH-261123-ABC123',
            'createdAt' => '2026-11-23T10:15:00Z',
            'paymentMethod' => 'COD',
            'paymentStatus' => 'PENDING',
            'currency' => 'eur',
            'subtotal' => 129.00,
            'shippingFee' => 0,
            'total' => 129.00,
            'shippingAddress' => [
                'fullName' => 'Sara Martin', 'phone' => '+33 6 12 34 56 78', 'addressLine' => '12 rue des Lilas',
                'city' => 'Lyon', 'postalCode' => '69001', 'country' => 'France',
            ],
            'shopOrders' => [[
                'shopName' => 'SoundWave Audio',
                'status' => 'PENDING',
                'subtotal' => 129.00,
                'trackingNumber' => null,
                'items' => [['name' => 'Wireless Noise-Cancelling Headphones', 'quantity' => 1, 'unitPrice' => 129.00, 'lineTotal' => 129.00]],
            ]],
        ];
    }
}
