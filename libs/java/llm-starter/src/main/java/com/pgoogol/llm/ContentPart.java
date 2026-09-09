package com.pgoogol.llm;

/**
 * Fragment treści wiadomości. Wiadomość jest listą fragmentów, a nie jednym
 * napisem, bo ekstrakcja ze zdjęcia wysyła tekst i obraz w tym samym zapytaniu.
 */
public sealed interface ContentPart permits TextPart, ImagePart {

}
