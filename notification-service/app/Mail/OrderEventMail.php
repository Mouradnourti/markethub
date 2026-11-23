<?php

namespace App\Mail;

use App\Services\InvoiceRenderer;
use Illuminate\Bus\Queueable;
use Illuminate\Mail\Attachment;
use Illuminate\Mail\Mailable;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;
use Illuminate\Queue\SerializesModels;

/** One e-mail per order event; the parcel concerned (if any) is highlighted. */
class OrderEventMail extends Mailable
{
    use Queueable, SerializesModels;

    public const EVENTS = ['ORDER_PLACED', 'ORDER_SHIPPED', 'ORDER_DELIVERED', 'ORDER_CANCELLED', 'ORDER_RETURNED'];

    private const COPY = [
        'ORDER_PLACED' => ['Your order %s is confirmed', 'Thank you for your order!',
            'We have received your order and passed it on to the sellers. Your invoice is attached.'],
        'ORDER_SHIPPED' => ['Your parcel from %s is on its way', 'Your parcel has shipped',
            'Good news: the seller handed your parcel to the carrier.'],
        'ORDER_DELIVERED' => ['Your parcel from %s was delivered', 'Delivered!',
            'Your parcel was delivered. We hope you enjoy it — you can now leave a review.'],
        'ORDER_CANCELLED' => ['Order %s: a parcel was cancelled', 'A parcel was cancelled',
            'Part or all of your order was cancelled. Any card payment for it will be refunded.'],
        'ORDER_RETURNED' => ['Order %s: return processed', 'Your return is processed',
            'The parcel was returned to the seller. Any card payment for it will be refunded.'],
    ];

    public function __construct(public array $payload, public ?string $invoicePdf = null)
    {
    }

    public function envelope(): Envelope
    {
        [$subject] = self::COPY[$this->payload['event']];
        $parcel = $this->parcel();
        $subject = in_array($this->payload['event'], ['ORDER_SHIPPED', 'ORDER_DELIVERED'], true) && $parcel
            ? sprintf($subject, $parcel['shopName'])
            : sprintf($subject, $this->payload['order']['reference']);

        return new Envelope(subject: $subject);
    }

    public function content(): Content
    {
        [, $headline, $intro] = self::COPY[$this->payload['event']];

        return new Content(view: 'emails.order-event', with: [
            'headline' => $headline,
            'intro' => $intro,
            'order' => $this->payload['order'],
            'recipient' => $this->payload['recipient'],
            'parcel' => $this->parcel(),
            'event' => $this->payload['event'],
        ]);
    }

    public function attachments(): array
    {
        if ($this->invoicePdf === null) {
            return [];
        }
        $pdf = $this->invoicePdf;

        return [
            Attachment::fromData(fn () => $pdf, InvoiceRenderer::fileName($this->payload['order']))
                ->withMime('application/pdf'),
        ];
    }

    private function parcel(): ?array
    {
        $index = $this->payload['parcelIndex'] ?? null;

        return $index === null ? null : ($this->payload['order']['shopOrders'][$index] ?? null);
    }
}
