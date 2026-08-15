package com.pgoogol.music.playlist;

/**
 * Dalszy ciąg gotowego setu (M4.4): stan sprzed uzupełnienia plus
 * propozycja tego, co dochodzi na koniec. Jak generator — <b>nic nie
 * zapisuje</b>; skład zmienia dopiero DJ przez {@code /api/playlists}.
 *
 * @param currentTrackCount ile utworów set ma teraz — pozycje w propozycji
 *                          zaczynają się właśnie od tej liczby
 * @param currentDurationMs ile set trwa teraz; {@code proposal.totalDurationMs()}
 *                          to długość po uzupełnieniu
 */
public record SetFill(int currentTrackCount, long currentDurationMs, SetProposal proposal) {

}
