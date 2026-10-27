# Backendi projekti struktuur

Kõik backendi kood asub kaustas `backend/src/main/java/ee/revkassa/`. Konventsioonid (nimetamine, vead, DTO reeglid): [backend/CLAUDE.md](../../backend/CLAUDE.md).

```
ee/revkassa/
├── RevkassaApplication.java
├── controller/
│   ├── <ressurss>/                     nt product, purchase, payment, member, event
│   │   ├── <Ressurss>Controller.java
│   │   └── dto/
│   │       ├── <Subjekt>Dto.java
│   │       └── <Subjekt>CreateRequestDto.java
│   ├── admin/<ressurss>/               admini endpointid (/api/admin/...), sama kujuga
│   │   ├── Admin<Ressurss>Controller.java
│   │   └── dto/
│   └── common/dto/                     DTO-d, mida kasutab mitu ressurssi
├── service/
│   └── <Ressurss>Service.java          äriloogika; nt AccountService, StockService
├── persistence/
│   └── <entiteet>/                     nt member, product, accountentry
│       ├── <Entiteet>.java             JPA entiteet
│       ├── <Entiteet>Repository.java
│       └── <Entiteet>Mapper.java       MapStruct
└── infrastructure/
    ├── error/                          ApiError, Error enum, RestExceptionHandler
    ├── exception/                      PrimaryKeyNotFoundException, DataNotFoundException, ForbiddenException
    ├── security/                       Spring Security, PIN ja admini sisselogimine, PinService
    ├── config/                         Clock bean, JSON seaded, @ConfigurationProperties
    ├── mail/                           Mailer liides, SMTP ja logiv implementatsioon
    └── ai/                             EventAssistant liides ja implementatsioonid
```

## Reeglid

- **Kontrolleri pakett** on ressursi järgi (`controller/product/`), admini endpointid paketis `controller/admin/<ressurss>/`, et liikme- ja admini-API oleksid selgelt lahus.
- **DTO** asub selle kontrolleri `dto/` alampaketis, mis seda kasutab. Kui DTO-d kasutab mitu ressurssi, tõsta see paketti `controller/common/dto/`.
- **Entiteet, repository ja mapper** asuvad koos paketis `persistence/<entiteet>/`. Paketi nimi on entiteedi nimi väiketähtedega ilma alakriipsuta (`account_entry` → `accountentry`).
- **Service** klassid on paketis `service/` (lame). Ajastatud tööd (`@Scheduled`, nt kuutasu) on samuti service klassid.
- **Infrastructure** ei sisalda äriloogikat — ainult tehniline läbiv osa ja välised liidesed.

## Ressursid

`backend/src/main/resources/application.properties` — andmebaas, sessioonid, `revkassa.*` seaded. Saladused (PIN-i võti, SMTP parool, `ANTHROPIC_API_KEY`) tulevad keskkonnamuutujatest.

## Andmebaas

Skeem ei ole koodis. Skeemi loovad SQL failid kaustas `docs/database/` (vt [DB-MODEL.md](../DB-MODEL.md)); Hibernate ainult valideerib (`ddl-auto=validate`).
