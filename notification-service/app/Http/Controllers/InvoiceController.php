<?php

namespace App\Http\Controllers;

use App\Http\Requests\InvoiceRequest;
use App\Services\InvoiceRenderer;
use Illuminate\Http\Response;

class InvoiceController extends Controller
{
    public function __invoke(InvoiceRequest $request, InvoiceRenderer $invoices): Response
    {
        $payload = $request->validated();
        $pdf = $invoices->render($payload['order'], $payload['customer']);

        return response($pdf, 200, [
            'Content-Type' => 'application/pdf',
            'Content-Disposition' => 'attachment; filename="'.InvoiceRenderer::fileName($payload['order']).'"',
        ]);
    }
}
