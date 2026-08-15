package com.pgoogol.music.playlist;

import com.pgoogol.music.catalog.ManualMetrics;
import com.pgoogol.music.catalog.TrackCatalog;
import org.springframework.lang.Nullable;

/**
 * Utwór na pozycji setu wraz z wyliczonym slotem. {@code djSlot} to slot
 * obowiązujący — czyli override DJ-a, a gdy go nie ma, wynik
 * {@link DjSlotCalculator}; {@code null} oznacza utwór jeszcze niewzbogacony.
 *
 * <p>{@code metrics} to <b>komplet</b> metryk wgranych z pliku, a nie
 * wybrane dwa pola jak do M4.5. Planer potrzebuje ich w całości:
 * ostrzeżenia liczą głośność i metrum, a falowe tryby układania — zmierzoną
 * energię, która jest liczbą 0..1, podczas gdy {@code track.energy} to zgrubne
 * {@code low/medium/high}. {@code null} znaczy „utwór nie był w żadnym
 * wgranym pliku".</p>
 */
public record PlannedTrack(
    int position,
    TrackCatalog track,
    @Nullable DjSlot djSlot,
    @Nullable String djSlotOverride,
    @Nullable ManualMetrics metrics) {

}
