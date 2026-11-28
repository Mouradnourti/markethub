<?php

namespace App\Mail;

use Illuminate\Bus\Queueable;
use Illuminate\Mail\Mailable;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;
use Illuminate\Queue\SerializesModels;

/** One friendly reminder for a cart left untouched. No fake urgency: it only lists what is in the cart. */
class CartReminderMail extends Mailable
{
    use Queueable, SerializesModels;

    public function __construct(public array $payload)
    {
    }

    public function envelope(): Envelope
    {
        return new Envelope(subject: 'You left something in your cart');
    }

    public function content(): Content
    {
        return new Content(view: 'emails.cart-reminder', with: [
            'cart' => $this->payload['cart'],
            'recipient' => $this->payload['recipient'],
        ]);
    }
}
