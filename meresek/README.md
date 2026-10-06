# Mérések

A Figyelő „Nyers adatok” zipjei. Újat ide (vagy a repó gyökerébe) lehet feltölteni: GitHub → Add file → Upload files.

| Fájl | Mi volt | Fő eredmény |
|---|---|---|
| 20261006-085436_Persona_3_Reload.zip | 1. mérés, 8,5 perc | medián 29 FPS (30-as limit), 1% low 8,5; esések: 53% fájlolvasás, 24% melegedés, 13% CPU; CPU-órajel végig ~71%-ra korlátozva |
| 20261006-093524_Persona_3_Reload.zip | 2. mérés ugyanott, 10,7 perc | 1% low 10,3, kevesebb esés (34 vs 44); 73% fájlolvasás; ugyanaz az órajel-korlát |
| 20261006-133230_Persona_3_Reload.zip | 3. mérés, 6 perc, Game Turbo + teljesítmény mód bekapcsolva | medián 30, 1% low 10,1; 84% fájlolvasás (memóriába leképezett lapok, a fő szál és FAsyncLoadingThread vár rájuk); órajel-plafon csak 71→75%; a játék a belső tárhelyről fut |

Beállítás mindháromnál: FEX 2609 (EXTREME), proton-10.0-arm64ec, VKD3D-Proton 3.0b, Turnip Gen8 V34, 1280×720.
A GPU-terhelés ezen a telefonon (HyperOS) nem olvasható.
