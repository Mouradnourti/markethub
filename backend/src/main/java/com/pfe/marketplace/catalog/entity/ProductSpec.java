package com.pfe.marketplace.catalog.entity;

import java.io.Serializable;

/** One line of a product's spec sheet ("Battery" → "5,000 mAh"), stored in the products.specs JSON column. */
public record ProductSpec(String label, String value) implements Serializable {
}
