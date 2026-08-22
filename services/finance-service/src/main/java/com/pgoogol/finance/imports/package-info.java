/**
 * Import wyciągów bankowych: wgranie, parsowanie, deduplikacja, podgląd,
 * zatwierdzenie i uzgodnienie salda.
 *
 * <p>Podpakiety układają się inaczej niż w pozostałych domenach modułu, bo są
 * tu dwa modele, nie jeden:</p>
 * <ul>
 *   <li>{@code statement} — model parsowania: plik, wiersz surowy, kwota, klucz
 *       deduplikacji i port {@code StatementParser}. Świadomie <b>bez Springa
 *       i bez JPA</b>, żeby dało się to testować bez kontekstu aplikacji i bez
 *       bazy; pilnuje tego {@code ArchitectureTest};</li>
 *   <li>{@code parser} — implementacje portu, po jednej na bank albo format;</li>
 *   <li>{@code domain} — encje JPA partii i wiersza, tak jak w każdej innej
 *       domenie serwisu;</li>
 *   <li>{@code application} — przypadki użycia, {@code infrastructure} — repozytoria.</li>
 * </ul>
 */
package com.pgoogol.finance.imports;
