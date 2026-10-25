# Owner and renter happy flows

Import **RentMyCar-Happy-Flows.postman_collection.json**. This separate collection covers only successful journeys and keeps the existing comprehensive test collection unchanged.

## Local setup

Start Docker and the app from the project root:

```powershell
docker compose up -d --wait
.\kotlin.bat run
```

In another PowerShell terminal, provision a fresh owner account and pickup/drop-off locations:

```powershell
.\postman\setup-happy-flow.ps1
```

Import `postman/HappyFlow.local.postman_environment.json` into Postman and select **RentMyCar Happy Flow - Local** as the active environment. This generated file contains local credentials and is ignored by Git. The script requires PowerShell, Docker Compose, and the running local app connected to that Compose database. It registers a unique account through the API (so its password is hashed), promotes only that new account through SQL, creates two locations, and verifies a new login returns OWNER. Every setup invocation creates a new owner and two locations.

This setup is necessary because public registration always creates a RENTER. There are no API endpoints for creating owners or locations. Do not add a `role` field to registration; that cannot create an owner. Existing owners can instead set `baseUrl`, `ownerEmail`, `ownerPassword`, `pickupId`, and `dropOffId` directly in the collection, using valid existing locations, and skip the script.

## Run the collection

Use Collection Runner, select all requests, and run **one iteration** in the listed order:

| Stage | Account | What happens |
| --- | --- | --- |
| 01 | Owner | Login, check profile, create a car at 12.50/hour with availability off, then enable rentals. |
| 02 | New renter | Register, login, check profile, browse the published car, book two hours, inspect PENDING reservation. |
| 03 | Owner | Login, view the renter's request, approve it as CONFIRMED. |
| 04 | Renter | Login, view confirmation and find it in the personal rental list. |
| 05 (optional) | Renter | Cancel the confirmed booking; skipped unless `cancelAfterFlow` is `true`. |

Owner and renter use separate saved tokens. The collection saves their user IDs, car ID, rental ID, unique plate and booking dates automatically. Booking is 10:00–12:00 Amsterdam local time on a date seven days ahead, sent without a timezone suffix. The expected total is 25.00. Each run uses a new car and renter to avoid conflicting bookings or plates. You can send the requests manually in the same order; refresh the relevant login when a token expires after one hour.

The owner's `GET /rentals` lists rentals where the owner is the renter; it is not an incoming-request inbox. This collection uses the saved `rentalId` to let the owner view and approve the renter's booking.

By default the final booking remains CONFIRMED, so you can inspect it afterward. Optional cancellation preserves its history; the API cannot delete rental records or delete a car with rental history. Runs leave their users, cars, locations and rentals in your development database.

Only put the five setup values in your active environment. Do not create environment/global variables named `ownerToken`, `renterToken`, `ownerId`, `renterId`, `renterEmail`, `carId`, `rentalId`, `licensePlate`, `startAt` or `endAt`; those would override the generated collection values. The requests contain response assertions, and stop the Runner after an unexpected HTTP status or login role. Clear saved tokens and passwords before sharing an export.

With Newman:

```powershell
npx --yes newman run postman/RentMyCar-Happy-Flows.postman_collection.json -e postman/HappyFlow.local.postman_environment.json --bail
```

Regenerate the collection after editing its source:

```powershell
node postman/generate-happy-flow.cjs
```
