package com.pfe.marketplace.auth.dto;

/** Roles a visitor may self-register with. Admins are never created through the public API. */
public enum AccountType {
    CUSTOMER, SELLER
}
