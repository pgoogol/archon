package com.pgoogol.llm;

/**
 * Ile modelowi wolno „pomyśleć" przed odpowiedzią. Wysyłane tylko wtedy, gdy
 * ktoś to ustawi — modele bez trybu rozumowania odrzucają ten parametr błędem.
 */
public enum Effort {

    LOW,
    MEDIUM,
    HIGH;

    public String wireValue() {

        return name().toLowerCase();
    }
}
