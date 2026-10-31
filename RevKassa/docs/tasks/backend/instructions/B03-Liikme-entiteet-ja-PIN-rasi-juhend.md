# Juhend: B03 · Liikme entiteet ja PIN-i räsi

**Taski kirjeldus:** `docs/BACKEND-TASKS.md` → B03; ärireeglid `docs/PLAN.md`, tabel `docs/DB-MODEL.md` → `member`
**Paketid:** `ee.revkassa.persistence.member`, `ee.revkassa.infrastructure.security`, `ee.revkassa.infrastructure.config`
**Implementeerimise voog:** Entiteet → Repository → salajane võti → juhuarvu bean → PinService (räsi → uus PIN → otsing) → testid → refactor

---

## Sissejuhatus

B03 ei ole endpoint, vaid vundament sisselogimisele (B04). Liige tuvastatakse ainult PIN-iga, seega peab
server leidma rea PIN-i järgi ilma PIN-i ennast salvestamata. Selleks salvestatakse PIN-i asemel selle
HMAC-SHA256 räsi serveri salajase võtmega. Selle harjutuse käigus õpid, kuidas kaardistada olemasolev
tabel JPA entiteediks, kuidas hoida saladus koodist väljas ja kuidas testida juhuslikkust.

**"Valmis, kui":** testid katavad räsi, unikaalsuse uue katse ja otsingu.

---

## Samm 1 — `Member` entiteet

### Mida teha?

Loo paketti `persistence/member/` entiteet tabelile `member`. Skeem on juba olemas
(`docs/database/2_create.sql`), rakendus ainult valideerib seda (`ddl-auto=validate`). Kui entiteet ja
tabel ei klapi, ei käivitu rakendus üldse.

Kasuta **JPA Buddy** abi: Database paneelil paremklõps tabelil `member` → **Create JPA Entity**, või
kirjuta klass käsitsi. Vaata tulemus igal juhul rida-realt üle.

```java
@Getter
@Setter
@Entity
@Table(name = "tabeli_nimi")
public class EntiteediKlass {

    @Id
    @GeneratedValue(strategy = GenerationType.MINGI_STRATEEGIA)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "veeru_nimi", nullable = false)
    private String väljaNimi;
}
```

> **Mõtle:**
> - `id` on `GENERATED ALWAYS AS IDENTITY`. Milline `GenerationType` sellele vastab?
> - `role` tohib olla ainult `MEMBER` või `ADMIN`. Mis Java tüüp seda paremini kaitseb kui `String`? Kuidas
>   tagada, et baasi läheb tekst, mitte järjekorranumber?
> - `version` on optimistlik lukk. Mis annotatsioon teeb sellest luku? B02-s testisid selle viga.
> - `created_at` ja `pin_changed_at` on `timestamptz`. Mis Java tüüp sobib? (Sama, mis B02 JSON-testis.)
> - `balance_cents`: vaata `backend/CLAUDE.md` domeenireegleid.

> **Tähelepanu 1:** Ära kasuta entiteedil `@Data`. See genereerib `toString()` meetodi, kuhu satuvad ka
> `pinHmac` ja `passwordHash`, ning üks logirida võib need paljastada. `@Getter` ja `@Setter` piisavad.

> **Tähelepanu 2:** `level_id` viitab tabelile `membership_level`. Kaardista see `@ManyToOne(fetch = LAZY)`
> seosena. Selleks loo juba praegu entiteet `MembershipLevel` omaette paketti `persistence/membershiplevel/`
> (B27 lisab sellele teenused ja endpointid).

---

## Samm 2 — `MemberRepository`

### Mida teha?

Loo samasse paketti repository interface. Vajad kahte küsimust: "leia liige räsi järgi" ja "kas selline
räsi on juba olemas?". Kumbki ei otsi `id` järgi, seega valmis `findById()` siin ei aita.

Kasuta **JPA Buddy** abi: **Find instance** esimese ja **Exists** teise jaoks, Advanced → **Named parameters**.

```java
public interface EntiteetRepository extends JpaRepository<Entiteet, IdTüüp> {

    @Query("select e from Entiteet e where e.väli = :väli")
    Optional<Entiteet> leiaMillegiJärgi(String väli);
}
```

> **Mõtle:** Rida on unikaalse indeksiga (`ux_member_pin_hmac`), nii et tulemusi saab olla 0 või 1. Mis
> tagastustüüp seda kõige ausamalt väljendab?

---

## Samm 3 — salajane võti

### Mida teha?

Võti on saladus: see ei tohi olla koodis ega `application.properties`-is. Spring Boot seob
keskkonnamuutuja `REVKASSA_PIN_SECRET` automaatselt seadega `revkassa.pin-secret`.

Nõue: **ilma võtmeta rakendus ei käivitu.** Parem kukkuda kohe käivitusel kui alles esimesel sisselogimisel.

> **Mõtle:**
> - Mis juhtub `@Value("${mingi.seade}")` korral, kui seadet pole ja vaikeväärtust ei ole antud?
> - Mis juhtub, kui muutuja on olemas, aga tühi? Kas ka see peaks peatama käivituse?
> - `@RequiredArgsConstructor` ei kopeeri `@Value` annotatsiooni konstruktorisse. Mis on lahendus?

> **Arenduses:** lisa IntelliJ Run Configuration'isse `REVKASSA_PIN_SECRET=dev-pin-secret`. Sama võtmega on
> tehtud räsid failis `docs/database/3_import.sql`.

> **Testides:** kõik `@SpringBootTest` testid vajavad nüüd võtit. Fail
> `src/test/resources/config/application.properties` lisab seade ainult testidele. Ära pane faili otse
> `src/test/resources/` kausta sama nimega: see varjaks kogu põhiseadistuse.

---

## Samm 4 — juhuarvu bean

### Mida teha?

B02-s õppisid, et aeg tuleb `Clock` beanist, sest muidu ei saa test seda kontrollida. Sama loogika kehtib
juhuarvu kohta. Kuidas muidu tõestada, et kokkupõrkel tehakse uus katse?

Loo paketti `infrastructure/config/` konfiguratsiooniklass, mis pakub `java.util.random.RandomGenerator`
beani. Vaata eeskujuks oma `ClockConfig`-i.

> **Mõtle:** PIN on turvaandmed. Kumb sobib: `java.util.Random` või `java.security.SecureRandom`? Miks?

---

## Samm 5 — `PinService`: räsi

### Mida teha?

Loo paketti `infrastructure/security/` klass `PinService`. Alusta ühest `private` meetodist, mis teeb
PIN-ist räsi: HMAC-SHA256, tulemus väiketähtedega hex-tekstina.

Vajalikud Java klassid: `javax.crypto.Mac`, `javax.crypto.spec.SecretKeySpec`, `java.util.HexFormat`.
Uuri nende meetodeid **Ctrl+Space** ja **Ctrl+P** abil.

> **Mõtle:**
> - `Mac` tahab baite. Kuidas muuta `String` baitideks nii, et tulemus ei sõltu masina seadistusest?
> - `PinService` on üks jagatud bean, aga `Mac` objekt ei ole lõimekindel. Kas tohib seda väljas hoida?
> - `Mac` viskab kontrollitud erindeid. Mida nendega teha? Need tähendavad vigast seadistust, mitte
>   kasutaja viga. Veateates ei tohi olla PIN-i.

> **Kontrollväärtus:** võtmega `dev-pin-secret` annab PIN `111111` räsi
> `022a92e11a89a2f073dbbe4578920272870cb113a551e1457781545bc05030b7`
> (Kaspar Kask failis `3_import.sql`). Kui saad sama tulemuse, on räsi õige.

---

## Samm 6 — `generateUniquePin()`

### Mida teha?

Meetod loob 6-numbrilise PIN-i, kontrollib repository abil, kas selle räsi on juba kasutusel, ja proovib
kokkupõrkel uuesti. Tagastab uue PIN-i.

> **Mõtle:**
> - Mis `nextInt` piir annab kõik väärtused `000000`–`999999`?
> - PIN on tekst. Mis saab väärtusest `42`, kui nõue on täpselt 6 märki? Kuidas tulevad algusnullid?
> - Milline tsükkel sobib, kui katsete arv pole ette teada?

---

## Samm 7 — `findByPin()`

### Mida teha?

Avalik meetod saab PIN-i, teeb sellest räsi ja küsib repositoryst liiget.

> **Mõtle:** Kas otsustad siin, mis saab, kui liiget ei leita? B04 peab sel juhul tegema midagi muud kui
> lihtsalt 404. Mida peaks see meetod seega tagastama?

---

## Samm 8 — testid

### Mida teha?

`backend/CLAUDE.md`: service loogika testitakse `@SpringBootTest`-iga päris Postgresi vastu, iga test
`@Transactional` (rullitakse tagasi).

```java
@SpringBootTest
@Transactional
class MingiServiceTest {

    @Autowired
    private TestitavService testitavService;

    @MockitoBean
    private AsendatavBean asendatavBean;

    @Test
    void mingiOlukord() {
        when(asendatavBean.meetod(argument)).thenReturn(esimeneVäärtus, teineVäärtus);

        TulemuseTüüp tulemus = testitavService.meetod();

        assertThat(tulemus).isEqualTo(oodatavVäärtus);
    }
}
```

> **Spring Boot 4 vihje:** `@MockitoBean` on paketis `org.springframework.test.context.bean.override.mockito`.

Testijuhtumid vähemalt:
- räsi: sama PIN annab sama räsi, mis `3_import.sql`-is
- uus PIN: täpselt 6 numbrit, ka väikese juhuarvu korral (algusnullid)
- kokkupõrge: esimene juhuarv on võetud → tagastatakse teine
- `findByPin`: õige PIN leiab liikme, tundmatu PIN annab tühja tulemuse

> **Mõtle:** Test vajab olemasolevat liiget. Kas toetud `3_import.sql` andmetele või lood rea testis endas?
> Mida tuleb arvestada veergudega `level_id` ja `created_at`?

---

## Samm 9 — kood ilusaks (refactor)

### Make it work → Make it beautiful

Vaata, kas `PinService`-is on korduvat koodi või liiga pikki meetodeid. Märgi lõik → **Ctrl+Alt+M**
(Extract Method) ja kontrolli, et abimeetod saaks ainult selle, mida vajab.

### Meetodite järjekord

1. `public` meetodid enne
2. `private` meetodid pärast
3. Järjesta ka väljakutsumise hierarhia järgi (skill `skill-jarjesta-meetodid`)

---

## Kokkuvõte ja kontrollnimekiri

- [ ] `Member` on paketis `persistence/member/`, kõik veerud on kaardistatud ja rakendus käivitub (`validate`)
- [ ] Entiteedil pole `@Data`-t ega `toString()`-i, kus on `pinHmac` või `passwordHash`
- [ ] `role` on enum ja salvestub tekstina, `version` on `@Version`
- [ ] `MemberRepository` kasutab `@Query` named parameters stiilis
- [ ] Ilma `REVKASSA_PIN_SECRET`-ita rakendus ei käivitu, võtit pole koodis
- [ ] Juhuarv tuleb beanist ja on krüptograafiliselt tugev
- [ ] `PinService` on paketis `infrastructure/security/`, räsi klapib `3_import.sql`-iga
- [ ] `generateUniquePin()` säilitab algusnullid ja proovib kokkupõrkel uuesti
- [ ] PIN-i ei logita ega panda ühessegi veateatesse
- [ ] Meetodite järjekord: `public` enne, `private` pärast
- [ ] `./gradlew test` läbib

---

> **Järgmine samm:** märgi B03 linnuke `docs/BACKEND-TASKS.md`-s ja liigu B04 juurde (turvalisus ja sisselogimine).
