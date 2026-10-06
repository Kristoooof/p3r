# GameNative Figyelő – projektleírás

Ez a repó a [GameNative](https://github.com/utkarshdalal/GameNative) (GPL-3.0) saját változata.
Cél: Windows-os PC-játékok gyorsítása Androidon, játékonként, mérések alapján.
Első célpont: Persona 3 Reload (UE4) a felhasználó POCO F7-én (Snapdragon 8s Gen 4, Adreno, HyperOS);
a menübetöltéskori 10–15 FPS-es esés megértése és javítása.

## Kommunikáció a felhasználóval

- Magyarul, egyszerűen, szakzsargon nélkül. Nem fejlesztő, főleg telefonról dolgozik.
- Minden lépés végén pontosan meg kell mondani, mit csináljon (melyik gomb a GitHubon, mit töltsön le, mit telepítsen).
- Kis, egyenként leforduló lépések. Egy fázis akkor kész, ha a GitHub Actions build zöld, és a felhasználó kipróbálta a telefonján.
- Törlés vagy force-push előtt rá kell kérdezni.

## Elvek és korlátok

- Root nélkül, Android 15/16-on (HyperOS) működjön.
- Az alapviselkedés ne változzon: minden új funkció kapcsolható, alapból kikapcsolva (kivéve a mérés gombját).
- A GPL-3.0 licenc és a THIRD_PARTY_NOTICES maradjon érintetlen; Box64/FEX módosításnál az ő licencük marad.
- A játék kódjából semmi nem kerül a repóba; a mérés csak címeket és statisztikát rögzít.
- A forkot időnként frissíteni kell az upstreamből: a módosítások legyenek elkülönítve
  (`figyelo/` mappa, `app.gamenative.figyelo` csomag), és minél kevesebb ponton érintsék a meglévő kódot.
- PostHog analitika: a mi buildünkben a kulcs üres, nem küld adatot.

## Szakmai háttér

- A CPU-fordító (Box64/FEX) egy x86 blokkot egyszer fordít és gyorsítótáraz; a forró kódnál a lefordított kód
  minősége számít, nem a fordítás sebessége.
- Valódi nyereség: fordító-beállítások játékra hangolása; forró függvények natív ARM-os cseréje;
  GPU-oldalon shadercsere (pl. FP16), drága effektek visszavétele, shader cache, driverválasztás.
- Nagyságrendek: CPU hangolás 10–30%, natív csere 30–50% a CPU-kötött részeken; GPU hangolás 5–20%,
  shadercsere 20–50%. Nem adódnak össze, a szűk keresztmetszet számít.
- A menübetöltési esés valószínű okai UE4 alatt: menet közbeni shaderfordítás (DXVK + driver),
  CPU-n futó kitömörítés a betöltő szálakon, lassú fájlolvasás. Mérés dönti el.
- Csak magas és közepes hozamú optimalizálásokkal foglalkozunk.

## Rendszerező logika (a Python `rendszerezo.py`-ból, meg kell tartani)

- Másodperces idővonal; „esés”: FPS < medián 60%-a, vagy 0 FPS (fagyás), vagy egy képkocka
  > max(100 ms, 4 × medián képkockaidő). Egymás melletti esések összevonása 1 mp-es réssel.
- Esésenkénti okpontozás: shaderre utaló naplósorok + shaderfordító szálak terhelése; betöltő szálak terhelése;
  háttértár-olvasás; egy szál ≥85% miközben GPU <75%; GPU ≥90%; CPU-órajel-plafon <75% (hő);
  szabad memória <600 MB. Fő ok = legnagyobb pontszám (≥0,4).
- Ha a telített szál maga shaderfordító vagy betöltő szál, az adott okként számít, nem általános CPU-kötöttségként.
- Javaslatok: MAGAS / KÖZEPES / ALACSONY; elöl csak magas és közepes, az alacsony lenyitható részben.
- Kimenetek: `jelentes.html` (grafikon: FPS, GPU, legterheltebb szál, esések, jelölések),
  `osszegzes.json`, `idovonal.csv`.
- Nyers adat (Python-kompatibilis JSONL): `minta.jsonl`, `kepkockak.jsonl`, `naplo.jsonl`, `jelek.jsonl`, `meta.json`.
- Ha a felhasználó feltölti a Python referenciát (`tools/figyelo-referencia/`), az ottani logikát és adatformátumot kell követni.

## Fázisok

### 0. fázis – saját build az eredeti mellé – KÉSZ (2026-10-05: telepítve, a P3R fut)

- `-Pfigyelo=true` Gradle-kapcsoló: `applicationIdSuffix = ".figyelo"`, név „GameNative Figyelő”
  (`figyelo/res`), állandó aláírókulcs (`figyelo/figyelo.keystore`, jelszó: `figyelo`).
- Workflow: `.github/workflows/figyelo-build.yml` – `bundleModernRelease` → bundletool universal APK → artifact.
- A kódban beégetett `/data/data/app.gamenative/...` útvonalak `BuildConfig.APPLICATION_ID`-re cserélve
  (upstream buildben a viselkedés azonos), és az `EVSHIM_BASE_PATH` beállítva, hogy a más csomagnevű telepítés is működjön.

### 1. fázis – beépített figyelő – ELKÉSZÜLT, kipróbálásra vár

Megvalósítás (`app/src/main/java/app/gamenative/figyelo/`):
- `FigyeloRogzito` (indítás/leállítás/jelölés, 1 Hz-es szál, automatikus leállás, ha a játék folyamatai eltűnnek),
  `Mintavevo` (rendszeradatok), `FigyeloMeta`, `FigyeloExport` (Letöltések/GameNativeFigyelo, megosztás, zip)
- `elemzes/` – a rendszerező Kotlinban (`Rendszerezo`, `Kimenet`, `JelentesHtml`, `SzalKategoria`), tiszta Kotlin + org.json
- `ui/FigyeloQuickMenuResz` – a gyorsmenü „Task Manager” fülének tetején; `ui/FigyeloJelentesActivity` – külön
  „Figyelő jelentések” ikon (csak `-Pfigyelo=true` buildben engedélyezve)
- Érintési pontok a meglévő kódban: `QuickMenu.kt` (3 sor + 2 `internal`), `AndroidManifest.xml` (activity),
  `file_provider_paths.xml`, `app/build.gradle.kts` (`figyeloEnabled` placeholder)
- Adatformátum: [ADATFORMATUM.md](ADATFORMATUM.md). A Python referencia nem került fel, ha felkerül, igazítani kell hozzá.

Eredeti terv:

- QuickMenu: „Mérés indítása/leállítása” és „Jelölés” gomb.
- ~1 Hz mintavétel háttérben (<2–3% többletterhelés): FPS/képkockaidők; `/proc/<pid>/task/*/stat` szálanként;
  CPU-órajelek; `/proc/stat`; kgsl GPU (`gpu_busy_percentage`, `gpuclk`) ha olvasható; thermal zónák, akku;
  `/proc/meminfo`; `/proc/<pid>/io`; Wine/DXVK napló időbélyeggel (`DXVK_HUD=fps,compiler`).
- Rendszerező Kotlinban, jelentés WebView-ban, Megosztás gomb; munkamenetek a Letöltések/GameNativeFigyelo mappába.
- Meta: eszköz, SoC, Android, konténer-beállítások, fordító (Box64/FEX, arm64ec/x64), driver, DXVK/VKD3D verzió.
- Kiindulópont: a meglévő `app/src/main/java/app/gamenative/utils/PerfSampler.kt`,
  `powercontrol/metrics/FrameTimeRing`, `GpuUsageSampler`, `SystemMetricsSources`, `gnoverlay`, `QuickMenu.kt`.

### 2. fázis – utasításszintű mérés (forró kódrészek)

- Saját Box64/FEX build mintavételező profilozóval (`timer_create` + `CLOCK_THREAD_CPUTIME_ID`, ~1 kHz),
  ARM PC → vendég x86 cím, zármentes gyűrűpuffer szálanként; cím → modul+RVA (`/proc/self/maps`).
- Fordítói statisztika (blokkok száma, fordítási idő). CI-ben épül, új választható verzióként (`box64-figyelo`).
- Kész, ha a jelentés mutatja a top 20 forró címtartományt modul+RVA formában, CPU-idő aránnyal.

### 3. fázis – játékprofil és optimalizálás

- Játékprofil (fordító-preset, driver, DXVK, shader cache), natív ARM-os cserék, shadercserék.
- Indítás a szabott profillal; ha az első percben összeomlik, újraindítás alapbeállításokkal, naplózva.

## Mérési eredmények (P3R, POCO F7, 2026-10-06, lásd meresek/)

- Fordító: FEX 2609 (EXTREME), proton-10.0-arm64ec, VKD3D-Proton 3.0b (DX12), Turnip Gen8 V34, 1280×720, 30 FPS limit.
- A játék a belső tárhelyről fut (`/data/data/<pkg>/CustomGames/...`, az import bemásolja).
- Medián 29–30 FPS, 1% low 8–10. Az esések 55–84%-a: a fő szál (`P3R.exe`) és a `FAsyncLoadingThread`
  memóriába leképezett lapokat olvas be (major laphiba ezres nagyságrendben/s, 40–230 MB/s), közben a render- és
  munkaszálak várnak. Szabad memória végig ~1 GB.
- A HyperOS végig ~71–75%-ra korlátozza a leggyorsabb mag órajelét (Game Turbo + teljesítmény móddal is).
- GPU sysfs (kgsl, /sys/kernel/gpu) létezik, de tiltott → GPU-kötöttség csak becsülhető.
- Szálnevek átjönnek (~80%): GameThread = `P3R.exe` fő szál, `RHIThread`, `RenderThread 1`, `TaskGraphThread`,
  `FAsyncLoadingThread`, `IoDispatcher`, `IoService`, `PoolThread N`, `vkd3d_queue`.
- A gyorsmenü megnyitása szünetelteti a játékot (SIGSTOP) → ezek a másodpercek nem esések.
- 4. mérés (fájl-gyorsítótár követéssel): a játék a `pakchunk0-WindowsNoEditor.ucas` (IoStore) fájlból olvas, a beolvasott
  adat gyorsan kiszorul (907 MB be / 1109 MB ki 8 perc alatt), a játék RSS-e 1021 → 335 MB-ig zsugorodik → **memóriahiány**,
  nem lassú tárhely. Gyanú: a korlátlan „Max Device Memory” (WRAPPER_VMEM_MAX_SIZE=0) miatt a játék nagy GPU-memóriát lát és
  sokat foglal. Következő kísérlet: Max Device Memory = 4096 MB.
- 5. mérés (Max Device Memory = 4096): nem javult (medián 25, 1% low 5,2). A P3R RSS-e csak ~0,5 GB (PC-n 3–4 GB), a többi
  valószínűleg zram-ban; a memóriát valami nem látott dolog foglalja (gyanú: GPU/driver, nem kiszorítható). A mérő v5 ezt
  `nemKovetett` néven becsli (MemTotal − ismert tételek), és rögzíti a GameNative saját RSS-ét.
- A 2. fázis (FEX profilozó) jelenleg alacsony hozamú: a telített fő szál az esésidő csak 1–15%-a.

## Nyitott kérdések

- Hol mérhető legmegbízhatóbban a képkockaidő (gnoverlay, renderer, swapchain hook, meglévő FrameTimeRing)?
- Olvashatók-e az appból a kgsl GPU-adatok és a thermal zónák HyperOS alatt?
- Átadja-e a Wine a Windows-os szálneveket (`GameThread`, `RenderThread`) a Linux-szálaknak?
- (Megválaszolva: a felhasználó konfigja FEX + arm64ec.) Melyik fájlból jönnek a laphibák (pak vagy exe/dll)? → `gyorsitotar` mező

## Upstream frissítés

```
git remote add upstream https://github.com/utkarshdalal/GameNative
git fetch upstream master
git merge upstream/master
```
