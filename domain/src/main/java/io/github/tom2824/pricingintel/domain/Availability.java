package io.github.tom2824.pricingintel.domain;

/** Disponibilité observée sur une annonce. Un prix hors stock ne doit pas peser dans l'analyse de marché. */
public enum Availability {
    IN_STOCK,
    OUT_OF_STOCK,
    PREORDER,
    BACKORDER,
    UNKNOWN
}
