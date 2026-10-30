# RevKassa backend — konventsioonid

Spring Boot 4, Java 21, Gradle. Pakett `ee.revkassa`. Failide paigutus: [docs/backend/projekti-struktuur.md](../docs/backend/projekti-struktuur.md).
Ärireeglid: [docs/PLAN.md](../docs/PLAN.md), tabelid: [docs/DB-MODEL.md](../docs/DB-MODEL.md).

## Kihid ja vastutus

- **Controller** (`controller/<ressurss>/`) — ainult HTTP: tee, parameetrid, `@Valid`, Swagger annotatsioonid, service väljakutse. Ei puutu repositooriume ega entiteete.
- **Service** (`service/`) — äriloogika, valideerimine, erindite viskamine, `@Transactional` kirjutavatel meetoditel. Tagastab DTO-sid, mitte entiteete.
- **Persistence** (`persistence/<entiteet>/`) — entiteet, repository, MapStruct mapper.
- **Infrastructure** (`infrastructure/`) — vead, turvalisus, konfiguratsioon, välised liidesed (e-post, AI).

## Nimetamine

- Kontroller `<Ressurss>Controller`, service `<Ressurss>Service`, repository `<Entiteet>Repository`, mapper `<Entiteet>Mapper`.
- DTO-d: `<Subjekt>Dto` (vastus), `<Subjekt>CreateRequestDto` / `<Subjekt>UpdateRequestDto` (sisend), vajadusel `<Subjekt>ResponseDto`.
- Mapperi meetodid: `to<Dto>(entiteet)`, listi jaoks mitmuses `to<Dto>s(list)`, entiteediks `to<Entiteet>(dto)`, uuendamiseks `update<Entiteet>(dto, @MappingTarget entiteet)`.
- Repository meetodid: lühike ja tähenduslik nimi + `@Query` named parameters stiilis (nt `findActiveProducts()`), mitte pikk tuletatud nimi.
- Service'is üksiku kirje kohustuslik leidmine: `getValid<Entiteet>By(id)` → viskab `PrimaryKeyNotFoundException`.
- Kood, API väljad, tabelid, enumid ja veateated (`ApiError.message`) inglise keeles. Eestikeelse teksti näitab kasutajale frontend `errorCode` järgi.

## DTO-d ja mapperid

- DTO on Lomboki `@Data` klass, mis rakendab `Serializable` (JPA Buddy genereeritud kuju), väljadel Bean Validation (`@NotNull`, `@Size`, `@Positive`, ...).
- Kontroller ei tagasta kunagi entiteeti.
- **Jagatud DTO-d:** kui DTO-d kasutab rohkem kui üks ressurss, kuulub see paketti `controller/common/dto/`.
- MapStruct mapper `@Mapper(componentModel = "spring")`. Iga target-väli on kaardistatud eksplitsiitselt kas `source`-iga (ka kattuva nime korral) või `ignore = true`-ga.
- `@Mapping` annotatsioonid käivad üksiku objekti meetodil; list-meetod jääb annotatsioonideta.

## Veakäsitlus

- Erindid on `infrastructure/exception/` all: `PrimaryKeyNotFoundException` (404, kirjet id järgi ei leitud), `DataNotFoundException` (404, päringu tulemus puudub), `ForbiddenException` (403, äri- või õigusreegel ei luba).
- Ärireegli konflikt (nt "tagasivõtmise aeg on möödas", vananenud versioon): `ForbiddenException` koos vastava `Error` enum'i väärtusega. Optimistlik lukk (`@Version`) annab 409.
- Vastuse keha on alati `ApiError { message, errorCode }`, vajadusel valikuliste väljadega (väljavead, `remainingAttempts`, `nextLockoutSeconds`), mis on JSON-is ainult väärtuse olemasolul. Arvulist infot, mida UI tekstis vajab, ei panda `message` sisse, vaid eraldi välja. `message` on ingliskeelne (arendajale ja logidele). `errorCode` on täisarv: frontend näitab selle järgi kasutajale eestikeelset teksti ja hargneb käitumist. Iga uus `Error` väärtus vajab seega ka frontendi vastendust.
- Kõik veateated ja koodid on ühes `Error` enum'is (`infrastructure/error/Error.java`), mitte laiali stringidena.
- `@RestControllerAdvice` (`infrastructure/error/RestExceptionHandler`) teisendab erindid ja valideerimisvead (400) `ApiError`-iks.

## Domeenireeglid, mida ei tohi mööda minna

- Raha on sentides `long` (SQL `bigint`), mitte kunagi `double`. Eurodeks vormindatakse ainult UI-s.
- Saldo muutub ainult `AccountService.post(...)` kaudu: kirjutab `account_entry` rea **ja** uuendab `member.balance_cents` samas transaktsioonis.
- Laoseis muutub ainult `StockService.move(...)` kaudu: kirjutab `stock_movement` rea **ja** uuendab `product.stock_qty`.
- Pearaamatu ridu (`account_entry`, `stock_movement`) ei muudeta ega kustutata; parandus on uus rida.
- PIN-i ei salvestata ega logita kunagi lihtsalt — ainult `pin_hmac`.
- AI abiline saab ainult ürituse andmed, mitte liikmete nimesid, e-posti ega saldosid.

## Swagger

Igal kontrolleri meetodil on `@Operation(summary = "...")` ja `@ApiResponses`, mis loetleb õnnestumise ja kõik taskis kirjeldatud veaolukorrad (`content = @Content(schema = @Schema(implementation = ApiError.class))`).

## Meetodite järjekord

`public` meetodid enne, `private` pärast; abimeetodid oma väljakutsuja järel väljakutsumise järjekorras (vt skill `skill-jarjesta-meetodid`).

## Testid

- Service loogika: `@SpringBootTest` päris Postgresi vastu, iga test `@Transactional` (rullitakse tagasi). Kontrolli DB-MODEL.md invariante.
- Kontroller: `@WebMvcTest` + `MockMvc` valideerimise, staatuskoodide ja `ApiError` keha jaoks.
- Aeg tuleb `Clock` beanist, et testid saaksid aega kontrollida.

## Käivitamine

Vt skill `run-revkassa`. WSL-is: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test` kaustas `backend/`.
