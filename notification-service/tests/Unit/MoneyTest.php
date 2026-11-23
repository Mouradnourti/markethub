<?php

namespace Tests\Unit;

use App\Services\InvoiceRenderer;
use App\Support\Money;
use PHPUnit\Framework\TestCase;

class MoneyTest extends TestCase
{
    public function test_formats_known_and_unknown_currencies(): void
    {
        $this->assertSame('€1,234.50', Money::format(1234.5, 'eur'));
        $this->assertSame('$9.90', Money::format('9.9', 'USD'));
        $this->assertSame('CHF 3.00', Money::format(3, 'chf'));
    }

    public function test_invoice_number_and_file_name_derive_from_the_order_reference(): void
    {
        $order = ['reference' => 'MH-261123-ABC123'];
        $this->assertSame('INV-261123-ABC123', InvoiceRenderer::invoiceNumber($order));
        $this->assertSame('invoice-MH-261123-ABC123.pdf', InvoiceRenderer::fileName($order));
    }
}
