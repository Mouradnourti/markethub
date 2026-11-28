<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>@yield('title')</title></head>
<body style="margin:0;padding:0;background:#f5f5f4;font-family:Inter,Segoe UI,Helvetica,Arial,sans-serif;color:#111111;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f5f5f4;padding:24px 0;">
    <tr><td align="center">
        <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #e7e5e4;">
            <tr><td style="background:#111111;padding:22px 28px;color:#ffffff;">
                <div style="font-size:20px;font-weight:800;letter-spacing:-0.02em;">Market<span style="color:#a78bfa;">Hub</span></div>
            </td></tr>
            <tr><td style="padding:28px;">
                @yield('content')
            </td></tr>
            <tr><td style="padding:16px 28px;background:#fafaf9;font-size:12px;color:#a8a29e;">
                @yield('footer', 'You receive this e-mail because you have a MarketHub account. This is an automated message.')
            </td></tr>
        </table>
    </td></tr>
</table>
</body>
</html>
