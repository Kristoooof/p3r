# Mérések

A Figyelő „Nyers adatok” zipjei. Újat ide (vagy a repó gyökerébe) lehet feltölteni: GitHub → Add file → Upload files.

| Fájl | Mi volt | Fő eredmény |
|---|---|---|
| 20261006-085436_Persona_3_Reload.zip | 1. mérés, 8,5 perc | medián 29 FPS (30-as limit), 1% low 8,5; esések: 53% fájlolvasás, 24% melegedés, 13% CPU; CPU-órajel végig ~71%-ra korlátozva |
| 20261006-093524_Persona_3_Reload.zip | 2. mérés ugyanott, 10,7 perc | 1% low 10,3, kevesebb esés (34 vs 44); 73% fájlolvasás; ugyanaz az órajel-korlát |
| 20261006-133230_Persona_3_Reload.zip | 3. mérés, 6 perc, Game Turbo + teljesítmény mód bekapcsolva | medián 30, 1% low 10,1; 84% fájlolvasás (memóriába leképezett lapok, a fő szál és FAsyncLoadingThread vár rájuk); órajel-plafon csak 71→75%; a játék a belső tárhelyről fut |
| 20261006-163442_Persona_3_Reload.zip | 4. mérés, 8 perc, háttérappok bezárva | medián 33, 1% low 10,5; 87% fájlolvasás: 4,9 GB beolvasás, a `pakchunk0-WindowsNoEditor.ucas`-ból 907 MB be / 1109 MB ki (újraolvasás); a játék RSS-e 1021 ↔ 335 MB között ingadozik → memóriahiány; Max Device Memory = 0 (korlátlan) |
| 20261006-204742_Persona_3_Reload.zip | 5. mérés, 9 perc, Max Device Memory = 4096 MB (még a mérő v4-gyel, memória-bontás nélkül) | medián 25, 1% low 5,2, esésidő 191 mp, 6,6 GB beolvasás; P3R RSS ~200–870 MB; a Wine-folyamatok elhanyagolhatók. **Nem összehasonlítható**: első Tartarus-látogatás, üres shader- és fájl-gyorsítótár → a 4096-os beállítás hatása eldöntetlen |
| 20261006-215506_Persona_3_Reload.zip | 6. mérés (mérő v5), 5,6 perc, Tartarus 2. látogatás, 100% render, Max Device Memory = 4096 | medián 32, 1% low 14,8, esésidő 15 mp; beolvasás csak 39 MB/perc (4. mérés: 600 MB/perc); maradék esések: melegedés (plafon 1920 MHz) |
| 20261006-222728_Persona_3_Reload.zip | 7. mérés (mérő v5), 5 perc, dorm, 75% render, Max Device Memory = 4096 | medián 30 (limit), 1% low 14,9, esésidő 5 mp; beolvasás 45 MB/perc |
| 20261007-075850_Persona_3_Reload.zip | 8. mérés (mérő v5), 6 perc, dorm, 75% render, Max Device Memory = 2048 | medián 34, 1% low 16,4, esésidő 12 mp; beolvasás 65 MB/perc; **CPU-plafon 99%** (most nem volt HyperOS-korlát) → a 2048 vs 4096 különbség nem mérhető, az FPS-javulás főleg a korlát hiányából jön |

Memória (6–7. mérés mediánja): 11,2 GB-ból elérhető ~1,2 GB; cserehelyen (zram) 7–7,7 GB, ebből a P3R 2,3–2,7 GB
(RSS csak 0,5–0,7 GB); „nem követett” ~7,5 GB = GPU/driver + zram saját memóriája (v6-tól külön mérve, ha olvasható).
**Következtetés:** a Max Device Memory = 4096 a meleg gyorsítótárral együtt kb. 15× kevesebb újraolvasást és
szinte esésmentes futást adott; a maradék esések főleg a melegedés miatti órajel-csökkenésből jönnek.

Beállítás mindegyiknél: FEX 2609 (EXTREME), proton-10.0-arm64ec, VKD3D-Proton 3.0b, Turnip Gen8 V34, 1280×720.
A GPU-terhelés ezen a telefonon (HyperOS) nem olvasható.

## Mérési módszer (összehasonlító mérésekhez)

- Mindig ugyanaz az ismert helyszín és útvonal (pl. dorm: földszint → emeletek → vissza, ~5 perc), ahol a shader cache már kész.
- Egy mérés = egy változtatás. Grafikai beállítás váltása után egy kör mérés nélkül (új shaderek), csak utána mérés.
- Új helyszín első bejárása nem hasonlítható a többihez.
