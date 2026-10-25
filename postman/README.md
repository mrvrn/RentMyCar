# RentMyCar Postman suite

For a shorter owner/renter walkthrough, use the separate [happy-flow collection and setup guide](HAPPY-FLOWS.md).

1. Start the database and Ktor application with `DEMO_SEED=true` (see [local setup](../docs/docker.md)). The application requires `JWT_SECRET` and defaults to port 8080.
2. Import `RentMyCar.postman_collection.json` into Postman.
3. Set the collection variables `baseUrl`, `carId`, `ownerEmail`, and `ownerPassword`. The owner credentials must belong to the owner of the selected car. Choose an existing available demo car with no booking during the test period, or create one using the fixture setup below.
4. Run the collection in order, with one iteration, using Collection Runner.

The following are demo-only values, loaded when `DEMO_SEED=true`: owner
`owner@example.com` / `Owner12345`, renter one `renter1@example.com` /
`Renter12345`, and renter two `renter2@example.com` / `Renter12345`. Select one
of the seeded `DEMO-001-A` through `DEMO-006-A` cars for `carId`; use the owner
credentials above for `ownerEmail` and `ownerPassword`. The collection defaults
to the demo owner values; override them for a non-demo environment.

To create a car fixture, log in through `POST /login` with an existing `OWNER` account and send `POST /cars` with its JWT as a Bearer token and `Content-Type: application/json`:

```json
{
  "brand": "Ford",
  "type": "Puma",
  "licensePlate": "TEST-01",
  "pricePerHour": 12.50,
  "pickupId": 1,
  "dropOffId": 2,
  "isAvailable": true
}
```

Replace `pickupId` and `dropOffId` with existing location IDs and use a unique license plate. Set `isAvailable` to `true`; it defaults to `false` when omitted. Set the collection's `carId` to the `id` in the `201 Created` response and use that owner's credentials for `ownerEmail` and `ownerPassword`. Direct database seeding of the owner, locations, and car remains an alternative for development fixtures.

Registration generates a unique email. Login automatically saves the JWT into `token`. Create rental uses `POST /cars/{{carId}}/rental` with that token and generates a 10:00–12:00 Amsterdam local-time period on a randomly selected UTC calendar date 7–371 days ahead, avoiding daylight-saving transition hours and collisions with earlier runs. Both the URL and body use the same `carId`. Dates have no timezone suffix, as required by the API. The response ID is saved as `rentalId`. All creation and rebooking requests use this car route.

Valid registration requests receive the same `200 OK` acknowledgement for new and existing email addresses. Request 08 checks this generic duplicate-registration response; it does not indicate whether an account was created. Existing accounts are not modified.

For manual use, send requests 01, 02, 03, and 04 in order. To use an existing account, set `email` and `password`, skip registration, and send requests 02, 03, and 04 in order. Request 03 initializes `pricePerHourCents`, which request 04 needs for its total-price assertion. Avoid environment variables named `email`, `password`, `token`, `startAt`, or `endAt`, since they override collection variables.

The collection covers registration, login, profiles, car management, rental creation, overlapping bookings, missing authentication, reversed dates, duplicate registration, invalid credentials, invalid registration data, shared validation, and owner-only consumption-cost and TCO endpoints. Status tests also cover owner login, renter access denied, unsupported transitions, unknown enum values, invalid and missing rental IDs, rejection, rebooking the rejected period, confirmation, and attempts to change decided reservations.

Request 02a verifies that a newly registered renter receives an empty reservation list before making any bookings. Request 03 saves the car's hourly rate, and request 04 checks that the two-hour total matches that rate and the renter ID matches the authenticated user. Request 02 extracts `userId` from the returned JWT for these checks; the API remains responsible for verifying its signature.

Requests 18 and 21 use `PATCH /rentals/{{rentalId}}/status` with a JSON body such as `{"status":"CONFIRMED"}` or `{"status":"REJECTED"}`. Owner login saves a separate `ownerToken`; the renter's `token` is preserved. Request 20 creates a replacement reservation for the rejected period and saves its ID in `rentalId`, so each successful decision starts from `PENDING`. For manual use, create a reservation, run request 13, then choose request 18 or 21.

Requests 23–33 test `PATCH /rentals/{{rentalId}}/cancel` without a body. They cover missing authentication, owner access denied, invalid and missing IDs, rejection of cancelling a rejected rental, cancellation of confirmed and pending rentals, preservation of the original timestamp on repeat cancellation, rejection of approving a cancelled rental, and rebooking the cancelled period. Request 18 saves `rejectedRentalId` for request 27. If continuing an older imported collection, set `rejectedRentalId` to the rental you rejected earlier before running 27. Requests 28 and 33 assert the returned status is `CANCELLED` and `cancelledAt` is populated.

Requests 34–35 are optional checks for `ACTIVE` and `COMPLETED` rentals. Set `activeRentalId` and `completedRentalId` to existing rentals belonging to the logged-in renter. They must already have those statuses: this API does not expose transitions to `ACTIVE` or `COMPLETED`. Each request skips itself when its ID is blank; a configured fixture must return 409. For a fresh registration, existing fixtures belong to a different user: use that fixture renter's `email` and `password` with request 02 and run 34–35 manually.

Requests 36–45 cover `GET /rentals/{{rentalId}}` and `GET /rentals`. They check the full rental details, the rejected and cancelled reservations in the renter's list, the nested car summary (`id`, `brand`, `type`, and nullable/omitted `pictures`), and that the owner's list excludes the renter's reservations. They also check missing authentication, nonexistent rentals (404), and nonnumeric, zero, negative, and overflowing IDs (400). These requests use the IDs and tokens saved earlier in the run; the final `rentalId` refers to the reservation cancelled in request 33.

Requests 46–54 check the car-route creation body against its URL, past and equal dates, malformed and incomplete rental bodies, invalid car IDs, malformed and missing status fields, and rejection of `CANCELLED` through the owner status endpoint. Cancellation uses the separate `/cancel` endpoint. These checks do not create additional reservations.

Request 55 verifies that the car owner can view reservation details for their car. The renter and car owner can read those details; unrelated users receive 403.

Requests 56–61 cover the user's own profile, absence of password fields, denial of another user's profile, missing authentication, invalid user IDs, and the current logout behavior. Logout returns 501 and does not revoke the JWT; request 61 verifies that the token still works.

Requests 62–74 cover blank required fields, oversized names, short/blank passwords, the 72-byte UTF-8 password limit, malformed or incomplete registration JSON, nonexistent numeric car IDs for lookup and booking, and malformed rental dates.

Requests 75–80 register and log in a second, unrelated user. They verify that this user cannot read, cancel, or approve the renter's reservation and receives an empty personal reservation list. This creates another persistent user per run. The renter and owner tokens are preserved.

Requests 81–87 reject malformed JWTs on every protected route type and a valid token with a tampered signature. These checks do not require the signing secret. Expired signed tokens and concurrent booking behavior remain covered by the Kotlin test suite rather than this sequential collection.

Request 88 is optional: set `unavailableCarId` to an existing car with `isAvailable=false` to verify that booking it returns 409 with `Car is unavailable`. It skips when no fixture ID is provided. Request 144 also tests this behavior using the car created by this collection, without requiring an optional fixture.

Requests 144–151 create a dedicated ICE car with consumption data and verify owner access to `/cars/{id}/consumption-cost` and `/cars/{id}/tco`, missing and invalid `kmPerYear` values, missing authentication, and renter access denial. The generated car uses 6.00 litres per 100 km and GASOLINE.

Requests 89–97 cover malformed and incomplete login bodies and the bcrypt 72-byte UTF-8 password boundary, including rejection of an oversized password sharing a valid prefix. The boundary checks create one additional persistent user.

Requests 98–149 cover public car listing and owner car management. Request 03 saves the selected car's location IDs for two dedicated management cars. The suite checks plate normalization and duplicates, authentication and renter restrictions, invalid fields and locations, decimal overflow and precision, invalid and missing IDs, full updates and persistence, rollback of failed updates, empty updates, clearing pictures, availability changes, and deletion. Hourly prices support up to `99999999.99` and at most two decimal places after removing trailing zeros; values such as `12.5000` are accepted. The suite deletes its two management cars and verifies they are gone. It also verifies that the selected booking car cannot be deleted because it has rental history. If a run is interrupted, the management cars may remain; use the saved `managedCarId` and `secondManagedCarId` to remove those dedicated fixtures manually.

Requests 150–154 are optional ownership checks. Configure `otherOwnerEmail` and `otherOwnerPassword` for an existing `OWNER` account that neither owns the selected car nor rented the tested reservation. The suite logs in separately and verifies that this owner cannot read or decide the rental, or update or delete the selected car. Public registration cannot create owner accounts, so these credentials must be provisioned separately. Without both credentials, these five requests skip. Clear these credentials and `otherOwnerToken` before sharing a collection export.

Requests 155–199 check the shared validation rules through both car creation and updates, then verify that rejected updates leave stored data unchanged. They use a third dedicated car, delete it afterward, and test accepted boundary values against the database. Registration checks reject malformed phone numbers and contact control characters, and accept a formatted international phone number using one additional persistent user.

Routes and repositories use the same car and registration validators. Car brand/type/plate values are trimmed and limited to 255 characters, with control characters rejected. Plates are uppercased before validating length and accept letters or numbers separated by single spaces or hyphens; this is a country-neutral syntax check, not a national registration check. Picture lists allow at most 20 absolute HTTP(S) URLs, each up to 2,048 characters, with no embedded credentials and a valid port when specified. An empty list clears pictures. Location IDs must be positive and refer to existing locations. Hourly prices must be greater than zero, including when calling the repository directly.

Registration trims contact fields, limits them to 100 characters, and rejects control characters. Phones must contain 7–15 digits, with an optional leading `+`, spaces, dots, hyphens, and balanced non-nested parentheses. This checks syntax rather than whether a number is assigned or reachable. Passwords remain untrimmed and must contain at least eight characters and at most 72 UTF-8 bytes.
Concurrent duplicate registrations and car creation are tested against MySQL in the Kotlin suite. The Postman collection runs sequentially. Active/completed rental fixtures remain optional because the API does not expose transitions into those states. Avoid environment variables shadowing generated `fixturePickupId`, `fixtureDropOffId`, `managedPlate`, `normalizedManagedPlate`, `managedCarId`, `secondManagedCarId`, `managedCarSnapshot`, `boundaryEmail`, `otherOwnerToken`, `validationCarId`, `validationCarSnapshot`, or `formattedPhoneEmail`.
Concurrent duplicate registrations and car creation are tested against MySQL in the Kotlin suite. The Postman collection runs sequentially. Active/completed rental fixtures remain optional because the API does not expose transitions into those states. Avoid environment variables shadowing generated `fixturePickupId`, `fixtureDropOffId`, `managedPlate`, `normalizedManagedPlate`, `managedCarId`, `secondManagedCarId`, `managedCarSnapshot`, `boundaryEmail`, `otherOwnerToken`, `validationCarId`, `validationCarSnapshot`, or `formattedPhoneEmail`.

Runs create persistent users and rentals, ending with the test reservations rejected or cancelled after request 33. Repeated runs can still overlap with other existing bookings; use a different car or remove previous test bookings from your development database. No cleanup endpoint is available. A 400 on car lookup means the ID is invalid; a 404 means the selected car does not exist; a 409 on creation means it is unavailable or already booked. JWTs expire after one hour. Request 02 refreshes the renter's `token`; request 13 refreshes the separate `ownerToken`. Avoid environment variables that shadow generated values such as `ownerToken`, `rentalId`, `rejectedRentalId`, `cancelledAt`, `userId`, `strangerEmail`, `strangerToken`, `invalidToken`, or `pricePerHourCents`. Clear passwords and saved tokens before sharing an exported collection.

With Newman installed, run from the project root:

```sh
newman run postman/RentMyCar.postman_collection.json -e postman/postman.local.environment.json
```

Create the local environment file outside version control with `baseUrl`, `carId`, `ownerEmail`, and `ownerPassword`, or configure those collection variables in Postman. Regenerate the collection from its source with `node postman/generate.cjs`.
