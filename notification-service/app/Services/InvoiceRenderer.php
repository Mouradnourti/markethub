<?php

namespace App\Services;

use Barryvdh\DomPDF\Facade\Pdf;

/** Renders an order as a PDF invoice (dompdf + Blade template resources/views/invoices/invoice.blade.php). */
class InvoiceRenderer
{
    public function render(array $order, array $customer): string
    {
        return Pdf::loadView('invoices.invoice', [
            'order' => $order,
            'customer' => $customer,
            'invoiceNumber' => self::invoiceNumber($order),
            'issuedAt' => now(),
        ])->setOption(['isFontSubsettingEnabled' => true])->setPaper('a4')->output();
    }

    public static function invoiceNumber(array $order): string
    {
        return 'INV-'.substr($order['reference'], 3);
    }

    public static function fileName(array $order): string
    {
        return 'invoice-'.$order['reference'].'.pdf';
    }
}
