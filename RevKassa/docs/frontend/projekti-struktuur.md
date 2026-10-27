# Frontendi projekti struktuur

Vue 3 + Vite, kaustas `frontend/`. Komponendid on kirjutatud Options API stiilis (vt [vue-komponendi-struktuur.md](vue-komponendi-struktuur.md)). Kogu UI tekst on eesti keeles ja kirjutatud otse komponentidesse.

```
frontend/src/
├── main.js                  rakenduse käivitus, router, axios'e seaded
├── App.vue                  valib navigatsiooni route'i meta järgi, <router-view>
├── router/
│   └── index.js             kõik route'id ja guard'id
├── views/                   üks fail iga route'i kohta, nimega <Nimi>View.vue
│   ├── PinView.vue          liikme vaated: ilma eesliiteta
│   ├── BuyView.vue
│   ├── AdminLoginView.vue   admini vaated: eesliide Admin
│   └── AdminProductsView.vue
├── components/
│   ├── common/              väikesed taaskasutatavad osad (AlertDanger, MoneyAmount, StatusPill, ...)
│   ├── forms/               vormid (ProductForm, PaymentForm, ...)
│   ├── modals/              dialoogid (ConfirmModal, NewPinModal, ...)
│   └── tables/              tabelid ja nimekirjad (ProductsTable, ParticipantsTable, ...)
├── navigation/
│   ├── MemberNavigation.vue  liikme päis + alumine tabiriba (Osta · Üritused · Konto · Logi välja)
│   ├── AdminNavigation.vue   admini ülemenüü (laual) / alumine tabiriba (telefonis)
│   └── NavigationService.js  ühised suunamised (nt navigateToPinView, navigateToAdminLogin)
├── api-services/
│   └── <Ressurss>Service.js  üks fail ressursi kohta, axios'e kutsed, tagastab promise'i
├── utils/
│   ├── format.js            formatEuro(cents), formatDate(date)
│   ├── session.js           sisselogitud liikme info sessionStorage'is
│   ├── basket.js            ostukorv sessionStorage'is
│   └── idleLogout.js        tegevusetuse taimer
└── assets/                  globaalne CSS (värvid, fondid), pildid
```

## Reeglid

- **Vaade = route.** Iga route'i komponent on `views/` all. Kõik, mis kordub või teeb vaate pikaks, läheb `components/` alla õigesse alamkausta.
- **Liikme ja admini vaated** on samas kaustas, admini omad eesliitega `Admin`. Route'i `meta` ütleb, kumb navigatsioon ja millist sessiooni on vaja (`meta: { member: true }` / `meta: { admin: true }`).
- **API kutsed ainult `api-services/` kaudu.** Komponendid ei kutsu axios'it otse. Service funktsioon tagastab axios'e promise'i; vaade lisab `.then()/.catch()/.finally()`.
- **axios'e seaded** on `main.js`-is: CSRF töötab automaatselt (Spring saadab `XSRF-TOKEN` küpsise, axios saadab `X-XSRF-TOKEN` päise). Response interceptor suunab 401 korral sisselogimisse (liige → PIN, admin → admini sisselogimine).
- **Veateade** tuleb backendist kujul `{ message, errorCode }` (`error.response.data`). Kasutajale näidatakse `message`, `errorCode` järgi hargneb käitumine (nt PIN-i lukustus).
- **Jagatud olek:**
  - andmed liiguvad vanemalt lapsele **props**-iga ja tagasi **emit**-iga;
  - sisselogitud liikme info (nimi, roll, saldo) on `sessionStorage`'is (`utils/session.js`), tõde on alati `GET /api/me`;
  - ostukorv on `sessionStorage`'is (`utils/basket.js`), sest see peab ostu- ja tellimusvaate vahel alles jääma; väljalogimisel see tühjendatakse.
  - Pinia't, composable'eid ega UI raamistikku ei kasutata.
- **Raha** tuleb sentides; vorminda ainult `formatEuro(cents)` abil (`27,40 €`). Kuupäevad `formatDate()` abil (`16.10.2026`).

## Paigutus

- **Liikme vaated on mobile-first:** põhi-CSS ~390px telefonile, `@media (min-width: 1024px)` reeglid 11" baaritahvlile (~1280×800 CSS px, landscape). Telefonis üks veerg, alumine tabiriba, kleepuv alumine tegevusriba, nupud ≥ 44px, numbriväljadel `inputmode`, ei midagi ainult hover'iga.
- **Admini vaated on desktop-first,** aga peavad telefonis toimima (tabelid `overflow-x: auto` kastis, tööriistaribad murduvad).

## Valmis, kui

`npm run build` õnnestub (ja `npm test`, kui muudatus puudutab `utils/` funktsioone) ning vaade on päris backendi vastu läbi proovitud 390px laiusel, liikme vaated ka 1280×800 juures, admini vaated laua laiusel.
