# Mérések

A Figyelő „Nyers adatok” zipjei. Újat ide (vagy a repó gyökerébe) lehet feltölteni: GitHub → Add file → Upload files.

| Fájl | Mi volt | Fő eredmény |
|---|---|---|
| 20261006-085436_Persona_3_Reload.zip | 1. mérés, 8,5 perc | medián 29 FPS (30-as limit), 1% low 8,5; esések: 53% fájlolvasás, 24% melegedés, 13% CPU; CPU-órajel végig ~71%-ra korlátozva |
| 20261006-093524_Persona_3_Reload.zip | 2. mérés ugyanott, 10,7 perc | 1% low 10,3, kevesebb esés (34 vs 44); 73% fájlolvasás; ugyanaz az órajel-korlát |
| 20261006-133230_Persona_3_Reload.zip | 3. mérés, 6 perc, Game Turbo + teljesítmény mód bekapcsolva | medián 30, 1% low 10,1; 84% fájlolvasás (memóriába leképezett lapok, a fő szál és FAsyncLoadingThread vár rájuk); órajel-plafon csak 71→75%; a játék a belső tárhelyről fut |
| 20261006-163442_Persona_3_Reload.zip | 4. mérés, 8 perc, háttérappok bezárva | medián 33, 1% low 10,5; 87% fájlolvasás: 4,9 GB beolvasás, a `pakchunk0-WindowsNoEditor.ucas`-ból 907 MB be / 1109 MB ki (újraolvasás); a játék RSS-e 1021 ↔ 335 MB között ingadozik → memóriahiány; Max Device Memory = 0 (korlátlan) |
| 20261006-204742_Persona_3_Reload.zip | 5. mérés, 9 perc, Max Device Memory = 4096 MB (még a mérő v4-gyel, memória-bontás nélkül) | medián 25, 1% low 5,2, esésidő 191 mp, 6,6 GB beolvasás; P3R RSS ~200–870 MB; a Wine-folyamatok elhanyagolhatók. **Nem összehasonlítható**: első Tartarus-látogatás, üres shader- és fájl-gyorsítótár → a 4096-os beállítás hatása eldöntetlen |

Beállítás mindegyiknél: FEX 2609 (EXTREME), proton-10.0-arm64ec, VKD3D-Proton 3.0b, Turnip Gen8 V34, 1280×720.
A GPU-terhelés ezen a telefonon (HyperOS) nem olvasható.

## Mérési módszer (összehasonlító mérésekhez)

- Mindig ugyanaz az ismert helyszín és útvonal (pl. dorm: földszint → emeletek → vissza, ~5 perc), ahol a shader cache már kész.
- Egy mérés = egy változtatás. Grafikai beállítás váltása után egy kör mérés nélkül (új shaderek), csak utána mérés.
- Új helyszín első bejárása nem hasonlítható a többihez.
