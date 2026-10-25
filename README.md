# RentMyCar

## Starten

Voer uit vanuit de projectmap:

```powershell
Copy-Item .env.example .env
docker compose up -d --wait
.\kotlin.bat run
```

De applicatie draait op [http://localhost:8080](http://localhost:8080).

## Migrations

Migrations worden automatisch uitgevoerd door Flyway wanneer de applicatie start:

```powershell
.\kotlin.bat run
```

De SQL-bestanden staan in `resources/db/migration/`. Stop de applicatie daarna met `Ctrl+C`.

## Tests

De CRUD- en authorization-tests (en alle andere tests) uitvoeren:

```powershell
.\kotlin.bat test
```

Voor de tests is Docker nodig, omdat Testcontainers MySQL gebruikt.
