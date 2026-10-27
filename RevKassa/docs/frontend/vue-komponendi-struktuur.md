# Vue komponendi struktuur

Kõik komponendid on kirjutatud **Options API** stiilis ühtse järjekorraga, et iga faili oleks lihtne lugeda.

## Järjekord

```vue
<template>
  ...
</template>

<script>
import MingiService from '@/api-services/MingiService'
import MingiKomponent from '@/components/common/MingiKomponent.vue'

export default {
  name: 'MinuVaade',
  components: { MingiKomponent },
  props: { ... },
  emits: ['event-midagi-juhtus'],
  data() {
    return { ... }
  },
  computed: { ... },
  methods: { ... },
  beforeMount() { ... }
}
</script>

<style scoped>
  ...
</style>
```

1. `name`
2. `components`
3. `props`
4. `emits`
5. `data`
6. `computed`
7. `methods` — avalikud/sündmuste käsitlejad enne, abimeetodid nende järel väljakutsumise järjekorras
8. elutsükli hookid (`beforeMount`, `beforeUnmount`)

## data

`data` on funktsioon, mis tagastab objekti. Kui andmed lähevad API-le või tulevad API-st, peegelda sama kuju nagu request/response DTO:

```js
data() {
  return {
    sisendObjekt: {
      valiYks: '',
      valiKaks: 0
    },
    elementideNimekiri: [],
    errorResponse: {
      message: '',
      errorCode: 0
    },
    isLoading: false
  }
}
```

## API päring

Service funktsioon `api-services/` all tagastab promise'i:

```js
import axios from 'axios'

export default {
  getElemendid() {
    return axios.get('/api/elemendid')
  },
  postElement(element) {
    return axios.post('/api/elemendid', element)
  }
}
```

Komponent kutsub seda `.then()/.catch()/.finally()` mustriga ja paneb tulemuse **kohe** `data` välja. `.then()` sees kasuta noolfunktsiooni, et `this` jääks komponendiks:

```js
methods: {
  laeElemendid() {
    this.isLoading = true
    MingiService.getElemendid()
      .then(response => this.elementideNimekiri = response.data)
      .catch(error => this.errorResponse = error.response.data)
      .finally(() => this.isLoading = false)
  }
}
```

## Andmete laadimine

Vaate esmased andmed laetakse `beforeMount`-is:

```js
beforeMount() {
  this.laeElemendid()
}
```

## Props ja emits

- Props on lapse jaoks ainult loetav. Last ei muuda propsi otse ega pane `v-model`-it propsi peale.
- Laps annab vanemale teada **emit**'iga. Sündmuse nimi on kebab-case `event-` eesliitega:

```js
emits: ['event-element-valitud'],
methods: {
  valiElement(element) {
    this.$emit('event-element-valitud', element)
  }
}
```

Vanem kuulab: `<ElementideTabel :elemendid="elementideNimekiri" @event-element-valitud="avaElement" />`

## Template

- Iga `v-for` element saab `:key`-ks andmete id, mitte indeksi.
- Tingimuslik sisu `v-if` / `v-else` abil (veateade, tühi nimekiri, laadimine).
- Nupp, mis saadab päringu, on päringu ajal keelatud (`:disabled="isLoading"`), et topeltvajutus ei saadaks seda kaks korda.
- Raha `formatEuro()` ja kuupäevad `formatDate()` abil, mitte template'is käsitsi.

## computed vs methods

Väärtus, mis tuletatakse teistest andmetest ja mida kuvatakse (nt korvi kogusumma), on `computed`. Tegevus (päring, suunamine, salvestamine) on `methods`.
