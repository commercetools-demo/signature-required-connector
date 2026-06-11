package com.commercetools.signature;

/** Builds minimal API Extension request envelopes for tests. */
public final class CartPayloads {

    public static final String OUR_TYPE_ID = "our-type-id";
    public static final String FOREIGN_TYPE_ID = "other-type-id";

    private CartPayloads() {}

    public static String normalItem() {
        return """
                {"id":"li-normal","productId":"p1","quantity":1,
                 "variant":{"id":1,"sku":"SKU1","attributes":[]}}""";
    }

    public static String narcoticItem() {
        return """
                {"id":"li-narcotic","productId":"p2","quantity":1,
                 "variant":{"id":1,"sku":"SKU2","attributes":[{"name":"narcotics","value":true}]}}""";
    }

    /** Our custom Type attached, with the flag field set to the given value. */
    public static String ourCustom(boolean flag) {
        return (",\"custom\":{\"type\":{\"typeId\":\"type\",\"id\":\"" + OUR_TYPE_ID
                + "\"},\"fields\":{\"signatureRequired\":" + flag + "}}");
    }

    /** A different custom Type attached (conflict). */
    public static String foreignCustom() {
        return ",\"custom\":{\"type\":{\"typeId\":\"type\",\"id\":\"" + FOREIGN_TYPE_ID + "\"},\"fields\":{}}";
    }

    public static final String NO_CUSTOM = "";

    /** Wrap line items + an optional custom block into a full Update envelope. */
    public static String envelope(String lineItemsCsv, String customBlock) {
        return """
                {"action":"Update","resource":{"typeId":"cart","id":"cart-1","obj":{
                  "id":"cart-1","version":1,"lineItems":[%s]%s}}}"""
                .formatted(lineItemsCsv, customBlock);
    }
}
