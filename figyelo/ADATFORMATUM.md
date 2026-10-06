# Figyelő – adatformátum (1. verzió)

Egy mérés egy mappa: `Android/data/app.gamenative.figyelo/files/figyelo/<ééééhhnn-óóppmm>_<játék>/`.
A mérés végén az app a fájlokat átmásolja ide: `Letöltések/GameNativeFigyelo/<mappa neve>/`.

Minden `.jsonl` fájlban soronként egy JSON objektum van; a `t` a mérés kezdete óta eltelt idő másodpercben.

## meta.json
- `formatum`, `alkalmazas`, `verzio`, `csomag`, `kezdes` (szöveg), `kezdesMs`, `mintavetelMs`, `jatek`
- `eszkoz`: `gyarto`, `modell`, `soc`, `hardver`, `android`, `sdk`, `build`, `kijelzoHz`, `magok`, `memoriaMb`,
  `klaszterek` (`[{magok:[..], maxMhz}]`, a CPU-klaszterek hardveres maximuma)
- `kontener`: `emulator` (Box64 / FEXCore), `box64Verzio`, `box64Preset`, `fexVerzio`, `fexPreset`, `wineVerzio`
  (arm64ec / x64 itt látszik), `wow64`, `driver`, `driverVerzio`, `driverBeallitas`, `dxwrapper` (DXVK/VKD3D verzió),
  `dxwrapperBeallitas`, `kepernyo`, `cpuLista`, `kornyezetiValtozok`, `exe`, …
  - `meghajtok`, `jatekMappa` (az A: meghajtó), `jatekMappaTipus`: `belso` / `appSajatKulso` / `megosztott` (FUSE) / `sdKartya`
- `olvashato`: mely rendszeradatok olvashatók (`procStat`, `gpuFrek`, `cpuFrek`), `gpuFajlok`: a GPU sysfs fájlok állapota
  (`olvashato: …` / `tiltott` / `nincs`)

## minta.jsonl (másodpercenként)
| kulcs | jelentés |
|---|---|
| `fps` | az adott másodperc képkockáinak száma / eltelt idő |
| `cpu`, `iow`, `magok` | `/proc/stat` alapú összes / I/O-várakozás / magonkénti terhelés % (ha olvasható) |
| `frek`, `frekMax` | klaszterenként `scaling_cur_freq` és `scaling_max_freq` (MHz) |
| `gpu`, `gpuMhz` | kgsl GPU-terhelés % és órajel (ha olvasható) |
| `hom` | `{cpu, akku, bor}` °C |
| `hoAllapot` | Android hőállapot (0 = nincs, 3 = súlyos, …) |
| `memSzabadMb` | szabad memória |
| `folyamatok` | `[{pid, nev, cpu, olv, rchar, mf, blk}]` – a játék oldali folyamatok (az app uid-ja), CPU % (100 = egy mag), `olv` = tárhelyről olvasott bájt/s, `rchar` = olvasott bájt/s gyorsítótárral, `mf` = major laphiba/s (memóriába leképezett fájlok olvasása), `blk` = I/O-várakozás % (csak ha a kernel méri), `rssMb` = memóriahasználat |
| `szunet` | 1, ha a GameNative épp szünetelteti a játékot (gyorsmenü / overlay, SIGSTOP) |
| `gyorsitotar` | ~5 mp-enként: `[[fájl, bent MB, méret MB], …]` – a játék nagy fájljaiból (pak, exe, dll, ≥16 MB) mennyi van a memóriában (mincore) |
| `szalak` | `[{pid, tid, nev, kat, cpu, all, mf}]` – a legterheltebb (≥1%) szálak, a laphibázó és a `D` (I/O-ra váró) állapotú szálak, max. 40; `kat`: shader, betoltes, render, jatek, munkaszal, hang, wine, egyeb |

## kepkockak.jsonl
`{t, ms:[…]}` – az adott másodpercben megjelenített képkockák ideje ms-ban (a GameNative X-szerver megjelenítési útvonalából, `FrameTimeRing`).

## naplo.jsonl
`{t, sor}` – a Wine / DXVK / VKD3D kimenet sorai (max. 200 sor/mp, soronként max. 400 karakter).

## jelek.jsonl
`{t, cimke}` – a felhasználó jelölései a gyorsmenüből (menü, betöltés, harc, egyéb).

## Kimenetek
- `jelentes.html` – grafikon (FPS, GPU, legterheltebb szál, esések, jelölések), javaslatok, esések, szálak
- `osszegzes.json` – gépi összegzés (esések okpontokkal, javaslatok, top szálak, adatforrások)
- `idovonal.csv` – másodperces idővonal

Az elemzés logikája: `app/src/main/java/app/gamenative/figyelo/elemzes/Rendszerezo.kt`
(lásd a szabályokat a [PROJEKT.md](PROJEKT.md)-ben). Az app „Újraelemzés” gombja a régi méréseket is
újraszámolja az aktuális szabályokkal.
