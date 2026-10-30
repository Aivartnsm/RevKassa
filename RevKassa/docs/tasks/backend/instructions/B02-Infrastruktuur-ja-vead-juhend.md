# Juhend: B02 · Infrastruktuur ja vead

**Taski kirjeldus:** `docs/BACKEND-TASKS.md` → B02; konventsioonid `backend/CLAUDE.md` → "Veakäsitlus"
**Paketid:** `ee.revkassa.infrastructure.error`, `ee.revkassa.infrastructure.exception`, `ee.revkassa.infrastructure.config`
**Implementeerimise voog:** Error enum → ApiError → erindid → RestExceptionHandler (erindid → valideerimine → optimistlik lukk) → Clock bean → JSON seaded → testid

---

## Sissejuhatus

B02 ei ole endpoint, vaid vundament, millele kõik järgmised endpointid toetuvad. Kui service avastab
probleemi (kirjet ei leitud, ärireegel ei luba), viskab ta erindi; üks keskne klass püüab selle kinni ja
teisendab alati sama kujuga JSON vastuseks `{ message, errorCode }`. Selle harjutuse käigus õpid, kuidas
Spring teeb veakäsitluse ühes kohas (`@RestControllerAdvice`), kuidas hoida kõik veateated ühes enum'is
ja kuidas testida kontrolleri kihti `MockMvc`-ga ilma andmebaasita.

**"Valmis, kui":** `@WebMvcTest` tõestab iga erindi staatuse ja `ApiError` keha.

---

## Samm 1 — `Error` enum

### Mida teha?

Kõik kasutajale nähtavad veateated ja nende koodid elavad ühes kohas — mitte laiali stringidena
service klassides. Loo paketti `infrastructure/error/` enum `Error`.

Igal enum väärtusel on kaks andmevälja: **ingliskeelne** sõnum (arendajale ja logidele) ja täisarvuline
kood. Eestikeelse teksti näitab kasutajale frontend koodi järgi. Enum'il saab olla
konstruktor ja väljad nagu tavalisel klassil:

```java
@Getter
@AllArgsConstructor
public enum VeaEnum {

    MINGI_VIGA("Some message in English.", 100),
    TEINE_VIGA("Another message.", 101);

    private final String message;
    private final int errorCode;
}
```

> **Mõtle:** Milliseid väärtusi on juba praegu teada? Vaata `docs/tasks/backend/` taskifailide
> "Veaolukorrad" tabeleid — seal on koodid ettepanekuna kirjas (nt vale PIN, mitteaktiivne konto).
> Lisaks vajad üldiseid: valideerimisviga, kirjet ei leitud, andmed on vahepeal muutunud (409),
> ootamatu serveri viga (500).
>
> **Kõik koodid ühes kohas:** taskitahvlil (RevKassa taskid) on nupp **"Veakoodid"** — seal on iga
> `errorCode`, selle HTTP staatus, ingliskeelne `message` ja taskid, mis seda kasutavad. B02 enda
> taskikaardil on üldised vead (101–105, 500). Alusta neist; ülejäänud lisad siis, kui teed vastavat
> taski.

> **Tähelepanu:** nimi `Error` kattub Java enda klassiga `java.lang.Error`. Mõtle, mis juhtub teistes
> failides, kus kasutad `Error.MINGI_VIGA` — milline `Error` seal mõeldud on? (Vihje: import.)

---

## Samm 2 — `ApiError` vastuse keha

### Mida teha?

Loo samasse paketti klass `ApiError` — see on vastuse DTO, mille UI saab igal vea korral.
Kohustuslikud väljad on taskifailide näidete järgi: `message` ja `errorCode`. Lisaks on mõnel veal
**valikulised** väljad — vaata `PIN-koodiga-sisselogimine.md` veaolukordi (`remainingAttempts`,
`nextLockoutSeconds`): UI vajab neid arve eestikeelse teksti jaoks.

```java
@Data
public class VastuseKlass {
    private String väljaÜks;
    private Integer väljaKaks;
}
```

> **Mõtle:** B02 ütleb "valideerimisvead 400 **koos väljadega**". Ka see on valikuline väli, mis
> ütleb, millised sisendväljad olid valed. Mis tüüpi see võiks olla, et UI saaks iga vormivälja alla õige teksti panna? Ja kuidas teha nii, et teiste vigade puhul seda
> JSON-isse üldse ei tuleks (vihje: Jacksoni `@JsonInclude`)? Sama küsimus kehtib `remainingAttempts` ja
> `nextLockoutSeconds` kohta — kas need peaksid olema `int` või `Integer`, kui "puudub" peab olema võimalik?

---

## Samm 3 — kolm erindit

### Mida teha?

Loo paketti `infrastructure/exception/` kolm erindiklassi:

| Erind | Millal | HTTP |
|---|---|---|
| `PrimaryKeyNotFoundException` | kirjet ei leitud id järgi (`getValid<Entiteet>By`) | 404 |
| `DataNotFoundException` | päringu tulemus puudub | 404 |
| `ForbiddenException` | ärireegel või õigus ei luba | 403 |

Erind on tavaline klass, mis laiendab `RuntimeException`-it. Mõtle, **mida erind endaga kaasas
kannab** — kas pelgalt teksti, või hoopis `Error` enum'i väärtust, millest saab hiljem nii sõnumi kui
koodi kätte?

```java
@Getter
public class MingiException extends RuntimeException {

    private final KaasasKantavTüüp väli;

    public MingiException(KaasasKantavTüüp väli) {
        super(/* mis siia sobib? */);
        this.väli = väli;
    }
}
```

> **Mõtle:** `PrimaryKeyNotFoundException` puhul — kas oleks kasulik teada ka, *mis* jäi leidmata
> (nt väljanimi ja väärtus) logi jaoks? Kasutajale läheb ikkagi `Error` enum'i tekst.

---

## Samm 4 — `RestExceptionHandler`: erindid

### Mida teha?

Loo paketti `infrastructure/error/` klass `RestExceptionHandler`. See on koht, kus kõik erindid
teisendatakse `ApiError`-iks — kontrollerid ja service'id ei pea sellest midagi teadma.

```java
@RestControllerAdvice
public class KesksedVeadKlass {

    @ExceptionHandler(MingiException.class)
    @ResponseStatus(HttpStatus.MINGI_STAATUS)
    public VastuseKlass kasitleMingiViga(MingiException exception) {
        // ehita vastuse objekt erindi sisust
    }
}
```

> **IntelliJ vihje:** kirjuta `@ResponseStatus(HttpStatus.` ja vajuta **Ctrl+Space** — näed kõiki
> staatuseid nimega (`NOT_FOUND`, `FORBIDDEN`, ...).

> **Mõtle:** Kolm erindit, kolm meetodit — aga vastuse ehitamise kood on igas sama. Kuhu see korduv
> osa võiks minna? (See on Samm 9 refactori kandidaat.)

---

## Samm 5 — `RestExceptionHandler`: valideerimisvead (400)

### Mida teha?

Kui kontrolleri meetodil on `@Valid @RequestBody` ja sisend ei läbi Bean Validationi (`@NotBlank`,
`@Pattern`, ...), viskab Spring ise erindi. Sinu ülesanne on see samuti kinni püüda ja teisendada
`ApiError`-iks staatusega 400.

> **Mõtle:**
> - Mis klassi erindi Spring `@Valid @RequestBody` puhul viskab? (Vihje: proovi testis, logist näed
>   täisnime; nimes on sõnad *MethodArgument* ja *NotValid*.)
> - Kust erindist saad kätte vigaste väljade nimed ja nende teated?
> - Mis juhtub, kui keha pole üldse loetav JSON (nt `{ "pin": `)? See on **teine** erind — ka see peaks
>   andma 400 `ApiError`-iga, mitte Springi vaikimisi vastuse.

---

## Samm 6 — `RestExceptionHandler`: optimistlik lukk (409) ja ootamatu viga (500)

### Mida teha?

Tabelitel `member`, `product` ja `event` on veerg `version` (`@Version`). Kui kaks adminit muudavad
sama rida, kukub teine salvestus — Spring viskab selle kohta oma erindi. Püüa see kinni ja tagasta 409
koos sobiva `Error` väärtusega ("Andmed on vahepeal muutunud, laadi leht uuesti" vms).

Lõpuks: üks üldine käsitleja kõigile ülejäänud erinditele → 500 `ApiError`-iga.

> **Mõtle:**
> - Mis klassi erind tuleb optimistliku luku korral? (Vihje: nimes on *ObjectOptimisticLocking*.)
> - 500 puhul: kas kasutajale tohib näidata erindi enda teksti? Mida peaks aga kindlasti **logisse**
>   kirjutama, et viga hiljem üles leida?

---

## Samm 7 — `Clock` bean

### Mida teha?

Ärireeglites on palju aega: tagasivõtmine 10 min jooksul, PIN-i lukustus, kuu tasu 1. kuupäeval. Kui
kood kutsub otse `LocalDateTime.now()`, ei saa test aega kontrollida. Seepärast tuleb aeg ühest
`Clock` beanist.

Loo paketti `infrastructure/config/` konfiguratsiooniklass, mis pakub `java.time.Clock` beani.

```java
@Configuration
public class MingiKonfiguratsioon {

    @Bean
    public TüüpMidaPakutakse beaniNimi() {
        return /* süsteemi kell — mis ajavööndiga? */;
    }
}
```

> **Mõtle:** PLAN.md ütleb, et kuutasu töö jookseb kell 06:00 *Europe/Tallinn*. Kas kell peaks olema
> UTC-s või Tallinna ajas? Ja kuidas test hiljem asendab selle fikseeritud kellaga
> (`Clock.fixed(...)`)?

---

## Samm 8 — JSON seaded

### Mida teha?

B02 nõuab: ajatemplid ISO-8601 kujul (`"2026-10-28T18:42:00+02:00"`, mitte number) ja raha
täisarvuliste sentidena (`-1250`, mitte `-12.5`).

> **Mõtle:**
> - Spring Boot 4 kasutab **Jackson 3**. Kas seal on kuupäevad vaikimisi juba ISO-8601? Kontrolli
>   testis või Swaggeris, enne kui midagi `application.properties`-sse lisad — ära lisa seadet, mida pole
>   vaja.
> - Raha on Javas `long`/`Long`. Kas Jackson muudab selle kunagi kümnendmurruks?
> - PIN-i task ütleb: `"pin": 123456` (number, mitte string) → 400. Vaikimisi teisendab Jackson numbri
>   vaikselt stringiks. See otsus kuulub B04 juurde, aga pea meeles, et JSON seadetes on see võimalik.

---

## Samm 9 — testid (`@WebMvcTest`)

### Mida teha?

"Valmis, kui": `@WebMvcTest` tõestab iga erindi staatuse ja `ApiError` keha. Päris kontrollereid veel
pole — seega loo **testikausta** väike testikontroller, mille iga endpoint viskab ühe erindi.

Testiklass `src/test/java/ee/revkassa/infrastructure/error/`:

```java
@WebMvcTest(TestiKontroller.class)
class MingiTestiKlass {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mingiOlukord() throws Exception {
        mockMvc.perform(get("/test/mingi-tee"))
                .andExpect(status().isMingiStaatus())
                .andExpect(jsonPath("$.väljaNimi").value(oodatavVäärtus));
    }
}
```

> **Spring Boot 4 vihje:** `@WebMvcTest` asub nüüd paketis
> `org.springframework.boot.webmvc.test.autoconfigure` (mitte enam `...boot.test.autoconfigure.web.servlet`).
> Kui IntelliJ ei leia importi, vajuta **Alt+Enter** ja vali õige pakett.

> **Mõtle:** Kas `@RestControllerAdvice` laetakse `@WebMvcTest`-is automaatselt? Kuidas testikontroller
> testi konteksti saab — kas see peab olema `@RestController` testiklassi sees või eraldi failis?

Testijuhtumid vähemalt:
- `PrimaryKeyNotFoundException` → 404 + `message` + `errorCode`
- `DataNotFoundException` → 404
- `ForbiddenException` → 403
- vigane `@Valid` sisend → 400 + väljade info
- loetamatu JSON → 400
- optimistlik lukk → 409
- ootamatu erind → 500, sõnum ei paljasta erindi teksti

---

## Samm 10 — kood ilusaks (refactor)

### Make it work → Make it beautiful

Kui kood töötab, on aeg vaadata, kas saab koodi puhtamaks muuta.

**Extract Method IntelliJ'ga:** `RestExceptionHandler`-is ehitab iga meetod `ApiError` objekti sama
moodi. Märgi korduv lõik → paremklõps → Refactor → Extract Method.

> **Tähelepanu:** IntelliJ kasutab ekstraktimisel kogu objekti parameetrina. Vaata üle, kas helper
> meetod vajab tegelikult kogu erindit või ainult `Error` väärtust — ja tee vajadusel korrektuur.

Enne:
```java
private VastuseKlass ehitaVastus(MingiException exception) {
    // kasutab ainult exception.getVäli()
}
```

Pärast (parem — anna edasi ainult vajalik):
```java
private VastuseKlass ehitaVastus(KaasasKantavTüüp väli) {
    // ...
}
```

### Meetodite järjekord

1. `public` meetodid enne
2. `private` meetodid pärast
3. Järjesta ka väljakutsumise hierarhia järgi — peameetod üleval, helper meetodid all (skill `skill-jarjesta-meetodid`)

---

## Kokkuvõte ja kontrollnimekiri

- [ ] `Error` enum on paketis `infrastructure/error/`, igal väärtusel ingliskeelne sõnum ja kood
- [ ] `ApiError` on paketis `infrastructure/error/` väljadega `message`, `errorCode` + valikulised (väljavead, `remainingAttempts`, `nextLockoutSeconds`), mis puuduvad JSON-ist, kui väärtust pole
- [ ] Kolm erindit on paketis `infrastructure/exception/` ja kannavad `Error` väärtust
- [ ] `RestExceptionHandler` (`@RestControllerAdvice`) käsitleb: 404, 404, 403, 400 (valideerimine), 400 (loetamatu JSON), 409, 500
- [ ] 500 vastus ei paljasta erindi teksti, aga viga logitakse
- [ ] `Clock` bean on paketis `infrastructure/config/`
- [ ] JSON: ajatemplid ISO-8601, raha täisarvuna sentides (kontrollitud, mitte oletatud)
- [ ] `@WebMvcTest` testid katavad iga olukorra staatuse ja `ApiError` keha
- [ ] Meetodite järjekord: `public` enne, `private` pärast
- [ ] `./gradlew test` läbib

---

> **Järgmine samm:** märgi B02 linnuke `docs/BACKEND-TASKS.md`-s ja liigu B03 juurde (`Member` entiteet ja `PinService`).
