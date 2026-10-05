# GameNative Figyelő – build

- APK készítése: GitHub → **Actions** → **GameNative Figyelo build** → **Run workflow**
  (vagy automatikusan minden push után). A kész APK a futás oldalán, az **Artifacts** résznél tölthető le (zip).
- Az APK `app.gamenative.figyelo` csomagnévvel, „GameNative Figyelő” néven települ, így az eredeti GameNative megmarad mellette.
- Helyi build: `./gradlew -Pfigyelo=true :app:bundleModernRelease`, majd bundletool universal APK (lásd a workflow-t).
- Az aláírókulcs (`figyelo.keystore`, jelszó `figyelo`) szándékosan a repóban van: így minden új build
  frissítésként települ a régire. Nem titok, csak a saját buildek egységes aláírására szolgál.
- A projekt teljes leírása: [PROJEKT.md](PROJEKT.md).
