<?php

namespace App\Support;

final class Money
{
    private const SYMBOLS = ['EUR' => '€', 'USD' => '$', 'GBP' => '£', 'MAD' => 'MAD '];

    public static function format(float|int|string $amount, string $currency): string
    {
        $code = strtoupper($currency);
        $symbol = self::SYMBOLS[$code] ?? $code.' ';

        return $symbol.number_format((float) $amount, 2, '.', ',');
    }
}
