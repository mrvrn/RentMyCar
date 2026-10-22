# RentMyCar Postman suite

1. Start the database and Ktor application (see [local setup](../docs/docker.md)). The application requires `JWT_SECRET` and defaults to port 8080.
2. Import `RentMyCar.postman_collection.json` into Postman.
3. Set the collection variables `baseUrl` and `carId`. Choose an existing available car with no booking during the test period. The API has no car creation endpoint; add a car and its related records to your development database if necessary.
4. Run the collection in order, with one iteration, using Collection Runner.

Registration generates a unique email. Login automatically saves the JWT into `token`. Create rental uses that token and generates a two-hour period seven days ahead. Both the URL and body use the same `carId`. Dates have no timezone suffix, as required by the API. The response ID is saved as `rentalId`.

For manual use, send requests 01, 02, and 04 in order. To log in with an existing account, set `email` and `password`, skip registration, and send request 02. Avoid environment variables named `email`, `password`, `token`, `startAt`, or `endAt`, since they override collection variables.

The ten requests cover registration, login, car lookup, rental creation, overlapping bookings, missing authentication, reversed dates, duplicate registration, invalid credentials, and invalid registration data.

Runs create persistent users and rentals. Repeated runs can overlap with earlier bookings; use a different car or remove previous test bookings from your development database. No cleanup endpoint is available. A 404 on car lookup means the selected car does not exist; a 409 on creation means it is unavailable or already booked. JWTs expire after one hour; log in again as needed. Clear saved tokens before sharing an exported collection.

With Newman installed, run from the project root:

```sh
newman run postman/RentMyCar.postman_collection.json --env-var baseUrl=http://localhost:8080 --env-var carId=1
```

Regenerate the collection from its source with `node postman/generate.cjs`.
