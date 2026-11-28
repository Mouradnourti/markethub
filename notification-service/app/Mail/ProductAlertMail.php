<?php

namespace App\Mail;

use App\Support\Money;
use Illuminate\Bus\Queueable;
use Illuminate\Mail\Mailable;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;
use Illuminate\Queue\SerializesModels;

/** A saved product is back in stock or cheaper than when the customer last heard about it. */
class ProductAlertMail extends Mailable
{
    use Queueable, SerializesModels;

    public const TYPES = ['BACK_IN_STOCK', 'PRICE_DROP'];

    public function __construct(public array $payload)
    {
    }

    public function envelope(): Envelope
    {
        $product = $this->payload['product'];
        $subject = $this->payload['type'] === 'PRICE_DROP'
            ? sprintf('Price drop: %s is now %s', $product['name'], Money::format($product['price'], $product['currency']))
            : sprintf('%s is back in stock', $product['name']);

        return new Envelope(subject: $subject);
    }

    public function content(): Content
    {
        return new Content(view: 'emails.product-alert', with: [
            'type' => $this->payload['type'],
            'product' => $this->payload['product'],
            'recipient' => $this->payload['recipient'],
        ]);
    }
}
