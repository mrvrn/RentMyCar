# RentMyCar Postman suite

1. Start the database and Ktor application (see [local setup](../docs/docker.md)). The application requires `JWT_SECRET` and defaults to port 8080.
2. Import `RentMyCar.postman_collection.json` into Postman.
3. Set the collection variables `baseUrl`, `carId`, `ownerEmail`, and `ownerPassword`. The owner credentials must belong to the owner of the selected car. Choose an existing available car with no booking during the test period. The API has no car creation endpoint; add a car and its related records to your development database if necessary.
4. Run the collection in order, with one iteration, using Collection Runner.

Registration generates a unique email. Login automatically saves the JWT into `token`. Create rental uses that token and generates a two-hour period seven days ahead. Both the URL and body use the same `carId`. Dates have no timezone suffix, as required by the API. The response ID is saved as `rentalId`.

For manual use, send requests 01, 02, and 04 in order. To log in with an existing account, set `email` and `password`, skip registration, and send request 02. Avoid environment variables named `email`, `password`, `token`, `startAt`, or `endAt`, since they override collection variables.

The 35 requests cover registration, login, car lookup, rental creation, overlapping bookings, missing authentication, reversed dates, duplicate registration, invalid credentials, and invalid registration data. Status tests also cover owner login, renter access denied, unsupported transitions, unknown enum values, invalid and missing rental IDs, rejection, rebooking the rejected period, confirmation, and attempts to change decided reservations.

Requests 18 and 21 use `PATCH /rentals/{{rentalId}}/status` with a JSON body such as `{"status":"CONFIRMED"}` or `{"status":"REJECTED"}`. Owner login saves a separate `ownerToken`; the renter's `token` is preserved. Request 20 creates a replacement reservation for the rejected period and saves its ID in `rentalId`, so each successful decision starts from `PENDING`. For manual use, create a reservation, run request 13, then choose request 18 or 21.

Requests 23–33 test `PATCH /rentals/{{rentalId}}/cancel` without a body. They cover missing authentication, owner access denied, invalid and missing IDs, rejection of cancelling a rejected rental, cancellation of confirmed and pending rentals, preservation of the original timestamp on repeat cancellation, rejection of approving a cancelled rental, and rebooking the cancelled period. Request 18 saves `rejectedRentalId` for request 27. If continuing an older imported collection, set `rejectedRentalId` to the rental you rejected earlier before running 27. Requests 28 and 33 assert the returned status is `CANCELLED` and `cancelledAt` is populated.

Requests 34–35 are optional checks for `ACTIVE` and `COMPLETED` rentals. Set `activeRentalId` and `completedRentalId` to existing rentals belonging to the logged-in renter. They must already have those statuses: this API does not expose transitions to `ACTIVE` or `COMPLETED`. Each request skips itself when its ID is blank; a configured fixture must return 409. For a fresh registration, existing fixtures belong to a different user: use that fixture renter's `email` and `password` with request 02 and run 34–35 manually.

Runs create persistent users and rentals, ending with the test reservations rejected or cancelled after request 33. Repeated runs can still overlap with other existing bookings; use a different car or remove previous test bookings from your development database. No cleanup endpoint is available. A 404 on car lookup means the selected car does not exist; a 409 on creation means it is unavailable or already booked. JWTs expire after one hour. Request 02 refreshes the renter's `token`; request 13 refreshes the separate `ownerToken`. Avoid environment variables that shadow `ownerEmail`, `ownerPassword`, `ownerToken`, `rentalId`, `rejectedRentalId`, or `cancelledAt`. Clear passwords and saved tokens before sharing an exported collection.

With Newman installed, run from the project root:

```sh
newman run postman/RentMyCar.postman_collection.json -e postman.local.environment.json
```

Create the local environment file outside version control with `baseUrl`, `carId`, `ownerEmail`, and `ownerPassword`, or configure those collection variables in Postman. Regenerate the collection from its source with `node postman/generate.cjs`.
