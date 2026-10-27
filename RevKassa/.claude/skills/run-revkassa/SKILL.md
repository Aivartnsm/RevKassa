---
name: run-revkassa
description: Käivita, ehita, testi või kontrolli RevKassa rakendust lokaalselt (PostgreSQL WSL-is, Spring Boot backend kaustas backend/, Vue/Vite frontend kaustas frontend/). Kasuta, kui on vaja rakendus käivitada, teste jooksutada, muudatust töös näha või kui andmebaas/Gradle ei käivitu.
---

# RevKassa käivitamine

Projekti juurkaust: kaust, kus on `backend/`, `frontend/` ja `docs/`.
Backend: `backend/` (Gradle). Frontend: `frontend/`.

## Keskkonna lõksud
- Windows seab `JAVA_HOME`-iks Windowsi JDK, mida WSL käivitada ei saa. `~/.bashrc` parandab
  selle, aga mitteinteraktiivsed shellid (nagu Claude'i Bash tööriist) seda ei lae. Lisa
  Gradle käskude ette: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew ...`
- Kasutaja käivitab asju ka IntelliJ-st Windowsis (Windowsi JDK, `gradlew.bat`). Gradle projekt
  on IntelliJ-s seotud kaustaga `backend/`. Ära muuda IntelliJ seadeid.
- `frontend/node_modules` on platvormipõhine. Kui `npm run dev` kukub puuduva natiivse sidumise
  tõttu, on see paigaldatud teisest OS-ist: kustuta `node_modules` ja tee uuesti `npm install`.

## 1. Andmebaas (PostgreSQL 16, systemd teenus WSL-is)
```bash
systemctl is-active postgresql || sudo systemctl start postgresql
PGPASSWORD=revkassa psql -h localhost -U revkassa -d revkassa -c 'select 1'
```
Andmebaas, kasutaja ja parool on kõik `revkassa`. Windows ulatub selleni aadressil `localhost:5432`.
Skeem tuleb failidest `docs/database/1_drop.sql`, `2_create.sql`, `3_import.sql` (vt skill
`muuda-andmebaasi-skeemi`). Kui tabelid puuduvad, küsi kasutajalt enne skriptide käivitamist —
`1_drop.sql` kustutab kõik andmed.

## 2. Backend
```bash
cd backend
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test        # testid (vajavad andmebaasi)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew bootRun     # :8080
```
Käivita `bootRun` taustal ja oota, kuni `curl -s localhost:8080/actuator/health` tagastab
`{"status":"UP"}`. Swagger UI: `http://localhost:8080/swagger-ui/index.html`. Peata see, kui oled valmis.

## 3. Frontend
```bash
cd frontend && npm run dev      # :5173, suunab /api ja /actuator pordile :8080
cd frontend && npm run build    # kompileerimise kontroll
```

## E-post arenduses
Ilma `spring.mail.host`-ita kirjutab rakendus arve e-kirjad logisse (otsi `Mailer` logiridu).
Ära kunagi suuna arendust päris SMTP serverile päris liikmete aadressidega.

## Muudatuse kontrollimine
- Backendi loogika: test, siis `./gradlew test`.
- API: `bootRun` + `curl` aadressil `localhost:8080/api/...` (turvatud teedel enne sisselogimine) või Swagger UI.
- UI: mõlemad serverid töötavad, siis ava `http://localhost:5173`.
Teata, mida päriselt käivitasid ja mis oli väljund; ütle, kui midagi ei saanud kontrollida.
