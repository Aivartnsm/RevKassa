# Väljalogimine

**Teenus:** `POST /api/auth/logout`

**Vaste mockupis:** [RevKassa Mockups](https://claude.ai/artifact/CiWBMreAkasT8zYQP9A222) — "Liikme rakendus — telefon" (`Main.dc.html`) ja "Liikme rakendus — tahvel" (`Tablet.dc.html`): tabiriba / päise nupp "Logi välja" ja ekraan "Ost tehtud" (`isDone`) nupp "Valmis, logi mind välja"; "Admin — telefon" (`AdminMobile.dc.html`) päise nupp "Logi välja"
**BE task:** B04 · **Kes:** kõik

## Sisend

Teenusel puuduvad sisendid (request body't, path ega query parameetreid pole). Sessioon tuleb
küpsisest. Päring nõuab CSRF tokenit (`X-XSRF-TOKEN` päis küpsise `XSRF-TOKEN` väärtusega).

## Väljund

**Response (204 No Content):** sessioon lõpetatakse, vastuse keha puudub.

- Serveri sessioon invalideeritakse (`HttpSession.invalidate()` / Spring Security `logout()`).
- Sessiooniküpsis kustutatakse (`Set-Cookie` aegunud küpsisega).
- CSRF küpsis `XSRF-TOKEN` kustutatakse samuti; järgmine päring saab uue tokeni (eelmise liikme
  tokenit ei jää tahvlisse).
- Lõpeb kogu sessioon — nii PIN- kui parooliga sessioon, sest brauseril on üks sessioon.
- Seadmeküpsist `REVKASSA_DEVICE` **ei** kustutata: tahvel jääb usaldatud seadmeks.
- Teenus on idempotentne: ka ilma sessioonita või aegunud sessiooniga päring → 204.
- Pärast väljalogimist naaseb UI avalehele `/` (usaldatud seadmes PIN-i klahvistik, mujal parooli vorm).

## Eesmärk

Ühine baaritahvel peab pärast igat ostu kohe järgmisele liikmele vabanema: liige vajutab "Valmis,
logi mind välja" või "Logi välja" ning järgmine inimene ei saa tema arvele osta. Server lõpetab
sessiooni, nii et vana küpsisega ei saa enam päringuid teha. (1-minutiline jõudeoleku aegumine on
lisakaitse samaks otstarbeks, vt `PIN-koodiga-sisselogimine.md`.)

## Seotud andmebaasi tabelid

Teenus andmebaasi ei puuduta: sessioonid hoitakse serveri mälus (Spring Boot vaikimisi
servlet-sessioon). Väljalogimine ei muuda `member` rida.

## Veaolukorrad

| Olukord | Status code | Response body | UI tekst (eesti, `errorCode` järgi) |
|---|---|---|---|
| CSRF token puudub või on vale | 403 Forbidden | `{ "message": "Access denied.", "errorCode": 118 }` (`SecurityErrorHandler`) | Midagi läks valesti. Värskenda lehte. |
| Ootamatu serveri viga | 500 Internal Server Error | `{ "message": "Unexpected server error.", "errorCode": 500 }` | Midagi läks valesti. Proovi uuesti. |

Puuduv või aegunud sessioon **ei ole** viga (204).

**Lahknevus lõuendiga:** "Admin — arvuti" (`Admin.dc.html`) päises nuppu "Logi välja" pole, telefoni
joonisel on. Endpoint on mõlemale sama; nupu lisamine on frontendi (F03 / admini paigutus) küsimus.

## Vastuvõtu kriteeriumid

- [x] Endpoint `POST /api/auth/logout` on olemas, ei nõua sisselogimist, nõuab CSRF tokenit.
- [x] PIN-sessiooniga päring → 204; sama küpsisega järgmine `GET /api/me` → 401.
- [x] Admini parooliga sessiooniga päring → 204; sama küpsisega järgmine `/api/admin/**` päring → 401.
- [x] Seadmeküpsis jääb alles: pärast väljalogimist töötab samas seadmes jälle PIN.
- [x] Vastus kustutab sessiooniküpsise ja `XSRF-TOKEN` küpsise.
- [x] Ilma sessioonita või aegunud sessiooniga päring → 204.
- [x] Ilma CSRF tokenita → 403, sessioon jääb kehtima.
- [x] MockMvc testid katavad kõik ülaltoodud juhtumid.
