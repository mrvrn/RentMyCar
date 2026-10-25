const fs = require('node:fs');
const path = require('node:path');
const event = (listen, code) => ({ listen, script: { type: 'text/javascript', exec: code.split('\n') } });
function request(name, url, body, status, options = {}) {
  const request = { method: options.method || 'POST', url: '{{baseUrl}}' + url,
    auth: options.auth ? { type: 'bearer', bearer: [{ key: 'token', value: '{{' + ({ owner: 'ownerToken', stranger: 'strangerToken', invalid: 'invalidToken' }[options.auth] || 'token') + '}}', type: 'string' }] } : { type: 'noauth' }, header: [] };
  if (body || options.rawBody !== undefined) {
    request.header.push({ key: 'Content-Type', value: 'application/json' });
    request.body = { mode: 'raw', raw: options.rawBody !== undefined ? options.rawBody : JSON.stringify(body, null, 2).replace('"__CAR_ID__"', '{{carId}}'), options: { raw: { language: 'json' } } };
  }
  return { name, request, event: [
    ...(options.pre ? [event('prerequest', options.pre)] : []),
    event('test', `pm.test('Status is ${status}', () => pm.response.to.have.status(${status}));\n` + (options.tests || ''))
  ] };
}
const user = { name: 'Postman Test User', email: '{{email}}', phone: '+31612345678', address: 'Teststraat 1, Amsterdam', password: '{{password}}' };
const rental = { carId: '__CAR_ID__', startAt: '{{startAt}}', endAt: '{{endAt}}' };
const createUrl = '/cars/{{carId}}/rental';
const detailUrl = '/rentals/{{rentalId}}';
const renterGet = { method: 'GET', auth: true };
const statusUrl = '/rentals/{{rentalId}}/status';
const cancelUrl = '/rentals/{{rentalId}}/cancel';
const ownerPatch = { method: 'PATCH', auth: 'owner' };
const renterPatch = { method: 'PATCH', auth: true };
const statusTests = status => `if (pm.response.code === 200) {
  const data = pm.response.json();
  pm.test('Correct rental returned', () => pm.expect(data.id).to.eql(Number(pm.variables.get('rentalId'))));
  pm.test('Status updated to ${status}', () => pm.expect(data.status).to.eql('${status}'));
}`;
const cancellationTests = `${statusTests('CANCELLED')}
if (pm.response.code === 200) {
  const data = pm.response.json();
  pm.test('Correct car returned', () => pm.expect(data.carId).to.eql(Number(pm.variables.get('carId'))));
  pm.test('Cancellation timestamp recorded', () => {
    pm.expect(data.cancelledAt).to.be.a('string');
    pm.expect(Number.isFinite(Date.parse(data.cancelledAt + 'Z'))).to.eql(true);
  });
  pm.collectionVariables.set('cancelledAt', data.cancelledAt);
}`;
const optionalCancellation = (variable, status) => ({
  ...renterPatch,
  pre: `if (!pm.variables.get('${variable}')) {
  console.info('Skipping ${status} cancellation check: set ${variable} to a rental belonging to the logged-in renter.');
  pm.execution.skipRequest();
}`,
  tests: `pm.test('${status} rental cannot be cancelled', () => {
  pm.expect(pm.response.text()).to.eql('Rejected, active or completed rentals cannot be cancelled');
});`
});
const collection = {
  info: { name: 'RentMyCar API tests', schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json', description: 'Run in order. Requires an existing available car. Creates persistent users and rentals. See the accompanying README.' },
  variable: Object.entries({ baseUrl: 'http://localhost:8080', carId: '1', email: '', password: 'PostmanTest123!', token: '', ownerEmail: '', ownerPassword: '', ownerToken: '', startAt: '', endAt: '', rentalId: '', rejectedRentalId: '', cancelledAt: '', activeRentalId: '', completedRentalId: '', userId: '', strangerEmail: '', strangerToken: '', invalidToken: 'invalid.jwt.token', pricePerHourCents: '', unavailableCarId: '' }).map(([key, value]) => ({ key, value, type: 'string' })),
  item: [
    request('01 Register a new user', '/register', user, 200, { pre: `pm.collectionVariables.set('email', 'postman.' + pm.variables.replaceIn('{{$guid}}') + '@example.com');
pm.collectionVariables.unset('token');
pm.collectionVariables.unset('ownerToken');
pm.collectionVariables.unset('rentalId');
pm.collectionVariables.unset('rejectedRentalId');
pm.collectionVariables.unset('userId');
pm.collectionVariables.unset('strangerToken');
pm.collectionVariables.unset('pricePerHourCents');
pm.collectionVariables.unset('cancelledAt');` }),
    request('02 Login and save token', '/login', { email: '{{email}}', password: '{{password}}' }, 200, { pre: `pm.collectionVariables.unset('token');`, tests: `if (pm.response.code === 200) {
  const data = pm.response.json();
  pm.test('JWT returned', () => pm.expect(data.token).to.be.a('string').and.match(/^[^.]+\\.[^.]+\\.[^.]+$/));
  if (typeof data.token === 'string') {
    pm.collectionVariables.set('token', data.token);
    const payload = JSON.parse(require('buffer').Buffer.from(data.token.split('.')[1], 'base64').toString());
    pm.collectionVariables.set('userId', payload.userId);
  }
}` }),
    request('02a New renter has no reservations', '/rentals', null, 200, { ...renterGet,
      tests: `pm.test('Empty reservation list', () => pm.expect(pm.response.json()).to.eql([]));` }),
    request('03 Check selected car', '/cars/{{carId}}', null, 200, { method: 'GET', tests: `if (pm.response.code === 200) {
  const data = pm.response.json();
  pm.collectionVariables.set('fixturePickupId', data.pickupId);
  pm.collectionVariables.set('fixtureDropOffId', data.dropOffId);
  pm.test('Correct car', () => pm.expect(data.id).to.eql(Number(pm.variables.get('carId'))));
  pm.test('Car is available', () => pm.expect(data.isAvailable).to.eql(true));
  pm.test('Hourly price is valid', () => pm.expect(data.pricePerHour).to.be.a('number').and.at.least(0));
  pm.collectionVariables.set('pricePerHourCents', Math.round(data.pricePerHour * 100));
}` }),
    request('04 Create rental through car route', createUrl, rental, 201, { auth: true, pre: `const start = new Date(Date.now() + 7 * 86400000);
start.setUTCHours(10, 0, 0, 0);
pm.collectionVariables.set('startAt', start.toISOString().slice(0, 19));
pm.collectionVariables.set('endAt', new Date(start.getTime() + 7200000).toISOString().slice(0, 19));
pm.collectionVariables.unset('rentalId');`, tests: `if (pm.response.code === 201) {
  const data = pm.response.json();
  pm.test('Rental ID returned', () => pm.expect(data.id).to.be.a('number').and.above(0));
  pm.test('Correct car and status', () => {
    pm.expect(data.carId).to.eql(Number(pm.variables.get('carId')));
    pm.expect(data.status).to.eql('PENDING');
  });
  pm.test('Requested period returned', () => {
    pm.expect(Date.parse(data.startAt + 'Z')).to.eql(Date.parse(pm.variables.get('startAt') + 'Z'));
    pm.expect(Date.parse(data.endAt + 'Z')).to.eql(Date.parse(pm.variables.get('endAt') + 'Z'));
  });
  pm.test('Total price returned', () => pm.expect(data.totalPrice).to.match(/^\\d+\\.\\d{2}$/));
  pm.test('Authenticated renter is used', () => pm.expect(data.renterId).to.eql(Number(pm.variables.get('userId'))));
  pm.test('Two hours charged at the hourly rate', () => {
    const cents = Number(pm.variables.get('pricePerHourCents')) * 2;
    pm.expect(data.totalPrice).to.eql((cents / 100).toFixed(2));
  });
  pm.collectionVariables.set('rentalId', data.id);
}` }),
    request('05 Reject overlapping rental', createUrl, rental, 409, { auth: true }),
    request('06 Reject rental without token', createUrl, rental, 401),
    request('07 Reject reversed dates', createUrl, { ...rental, startAt: '{{endAt}}', endAt: '{{startAt}}' }, 400, { auth: true }),
    request('08 Acknowledge duplicate registration without disclosure', '/register', user, 200, {
      tests: `pm.test('Generic registration acknowledgement', () => pm.expect(pm.response.text()).to.eql('Registration request processed.'));`
    }),
    request('09 Reject invalid login', '/login', { email: '{{email}}', password: 'deliberately-wrong-password' }, 401),
    request('10 Reject invalid registration', '/register', { ...user, email: 'invalid-email' }, 400),
    request('11 Reject status update without token', statusUrl, { status: 'CONFIRMED' }, 401, { method: 'PATCH' }),
    request('12 Reject status update by renter', statusUrl, { status: 'CONFIRMED' }, 403, { method: 'PATCH', auth: true }),
    request('13 Login as car owner', '/login', { email: '{{ownerEmail}}', password: '{{ownerPassword}}' }, 200, {
      pre: `pm.collectionVariables.unset('ownerToken');`,
      tests: `if (pm.response.code === 200) {
  const data = pm.response.json();
  pm.test('Owner JWT returned', () => pm.expect(data.token).to.be.a('string').and.match(/^[^.]+\\.[^.]+\\.[^.]+$/));
  if (typeof data.token === 'string') pm.collectionVariables.set('ownerToken', data.token);
}` }),
    request('14 Reject unsupported status transition', statusUrl, { status: 'COMPLETED' }, 400, ownerPatch),
    request('15 Reject unknown status value', statusUrl, { status: 'UNKNOWN' }, 400, ownerPatch),
    request('16 Reject invalid rental ID', '/rentals/invalid/status', { status: 'CONFIRMED' }, 400, ownerPatch),
    request('17 Reject missing rental', '/rentals/9223372036854775807/status', { status: 'CONFIRMED' }, 404, ownerPatch),
    request('18 Owner rejects reservation', statusUrl, { status: 'REJECTED' }, 200, { ...ownerPatch, tests: `${statusTests('REJECTED')}
if (pm.response.code === 200) pm.collectionVariables.set('rejectedRentalId', pm.response.json().id);` }),
    request('19 Reject changing a rejected reservation', statusUrl, { status: 'CONFIRMED' }, 409, ownerPatch),
    request('20 Rebook rejected period', createUrl, rental, 201, { auth: true,
      pre: `pm.collectionVariables.unset('rentalId');`,
      tests: `if (pm.response.code === 201) {
  const data = pm.response.json();
  pm.test('New pending rental returned', () => {
    pm.expect(data.id).to.be.a('number').and.above(0);
    pm.expect(data.status).to.eql('PENDING');
    pm.expect(data.carId).to.eql(Number(pm.variables.get('carId')));
  });
  pm.collectionVariables.set('rentalId', data.id);
}` }),
    request('21 Owner confirms reservation', statusUrl, { status: 'CONFIRMED' }, 200, { ...ownerPatch, tests: statusTests('CONFIRMED') }),
    request('22 Reject changing a confirmed reservation', statusUrl, { status: 'REJECTED' }, 409, ownerPatch),
    request('23 Reject cancellation without token', cancelUrl, null, 401, { method: 'PATCH' }),
    request('24 Reject cancellation by car owner', cancelUrl, null, 403, ownerPatch),
    request('25 Reject cancellation with invalid rental ID', '/rentals/invalid/cancel', null, 400, renterPatch),
    request('26 Reject cancellation of missing rental', '/rentals/9223372036854775807/cancel', null, 404, renterPatch),
    request('27 Reject cancellation of rejected rental', '/rentals/{{rejectedRentalId}}/cancel', null, 409, {
      ...renterPatch,
      pre: `if (!pm.variables.get('rejectedRentalId')) {
  throw new Error('Set rejectedRentalId to the rental rejected in request 18, or run requests 01-22 first.');
}`,
      tests: `pm.test('Rejected rental cannot be cancelled', () => {
  pm.expect(pm.response.text()).to.eql('Rejected, active or completed rentals cannot be cancelled');
});`
    }),
    request('28 Renter cancels confirmed rental', cancelUrl, null, 200, { ...renterPatch, tests: cancellationTests }),
    request('29 Repeated cancellation preserves timestamp', cancelUrl, null, 200, {
      ...renterPatch,
      tests: `${statusTests('CANCELLED')}
if (pm.response.code === 200) {
  pm.test('Original cancellation timestamp preserved', () => {
    const original = Date.parse(pm.variables.get('cancelledAt') + 'Z');
    const repeated = Date.parse(pm.response.json().cancelledAt + 'Z');
    // Allow for sub-millisecond precision differences between the DAO and MySQL.
    pm.expect(Math.abs(repeated - original)).to.be.at.most(1);
  });
}`
    }),
    request('30 Reject approval of cancelled rental', statusUrl, { status: 'CONFIRMED' }, 409, ownerPatch),
    request('31 Rebook cancelled period', createUrl, rental, 201, {
      auth: true,
      tests: `if (pm.response.code === 201) {
  const data = pm.response.json();
  pm.test('Replacement rental is pending', () => pm.expect(data.status).to.eql('PENDING'));
  pm.test('New rental created', () => pm.expect(data.id).to.be.a('number').and.above(0));
  pm.test('Same car and period rebooked', () => {
    pm.expect(data.carId).to.eql(Number(pm.variables.get('carId')));
    pm.expect(Date.parse(data.startAt + 'Z')).to.eql(Date.parse(pm.variables.get('startAt') + 'Z'));
    pm.expect(Date.parse(data.endAt + 'Z')).to.eql(Date.parse(pm.variables.get('endAt') + 'Z'));
  });
  pm.collectionVariables.set('rentalId', data.id);
}`
    }),
    request('32 Reject owner cancelling pending rental', cancelUrl, null, 403, ownerPatch),
    request('33 Renter cancels pending rental', cancelUrl, null, 200, { ...renterPatch, tests: cancellationTests }),
    request('34 Reject cancellation of active rental (optional)', '/rentals/{{activeRentalId}}/cancel', null, 409,
      optionalCancellation('activeRentalId', 'ACTIVE')),
    request('35 Reject cancellation of completed rental (optional)', '/rentals/{{completedRentalId}}/cancel', null, 409,
      optionalCancellation('completedRentalId', 'COMPLETED')),
    request('36 Get rental details', detailUrl, null, 200, { ...renterGet,
      tests: `${statusTests('CANCELLED')}
if (pm.response.code === 200) {
  const data = pm.response.json();
  pm.test('Rental details contain car, renter, dates and price', () => {
    pm.expect(data.car.id).to.eql(Number(pm.variables.get('carId')));
    pm.expect(data.car.brand).to.be.a('string');
    pm.expect(data.car.type).to.be.a('string');
    pm.expect(data).not.to.have.property('carId');
    pm.expect(data.renterId).to.be.a('number').and.above(0);
    pm.expect(Date.parse(data.startAt + 'Z')).to.eql(Date.parse(pm.variables.get('startAt') + 'Z'));
    pm.expect(Date.parse(data.endAt + 'Z')).to.eql(Date.parse(pm.variables.get('endAt') + 'Z'));
    pm.expect(data.totalPrice).to.match(/^\\d+\\.\\d{2}$/);
    pm.expect(data.createdAt).to.be.a('string');
    pm.expect(data.cancelledAt).to.be.a('string');
  });
}` }),
    request('37 List renter reservations with car summaries', '/rentals', null, 200, { ...renterGet,
      tests: `if (pm.response.code === 200) {
  const data = pm.response.json();
  pm.test('Rental overview is an array', () => pm.expect(data).to.be.an('array'));
  pm.test('Created reservations are included with their final statuses', () => {
    const cancelled = data.find(row => row.id === Number(pm.variables.get('rentalId')));
    const rejected = data.find(row => row.id === Number(pm.variables.get('rejectedRentalId')));
    pm.expect(cancelled).to.be.an('object');
    pm.expect(cancelled.status).to.eql('CANCELLED');
    pm.expect(rejected).to.be.an('object');
    pm.expect(rejected.status).to.eql('REJECTED');
  });
  pm.test('Each overview contains rental dates, price and a nested car summary', () => {
    data.forEach(row => {
      pm.expect(row.id).to.be.a('number').and.above(0);
      pm.expect(Number.isFinite(Date.parse(row.startAt + 'Z'))).to.eql(true);
      pm.expect(Number.isFinite(Date.parse(row.endAt + 'Z'))).to.eql(true);
      pm.expect(row.totalPrice).to.match(/^\\d+\\.\\d{2}$/);
      pm.expect(row.car.id).to.be.a('number').and.above(0);
      pm.expect(row.car.brand).to.be.a('string');
      pm.expect(row.car.type).to.be.a('string');
      // Kotlin serialization may omit a null optional picture.
      if (row.car.pictures != null) pm.expect(row.car.pictures).to.be.a('string');
    });
    const created = data.find(row => row.id === Number(pm.variables.get('rentalId')));
    pm.expect(created.car.id).to.eql(Number(pm.variables.get('carId')));
  });
}` }),
    request('38 Owner list excludes renter reservations', '/rentals', null, 200, { method: 'GET', auth: 'owner',
      tests: `if (pm.response.code === 200) {
  const data = pm.response.json();
  pm.test('Only reservations rented by the logged-in owner are listed', () => {
    pm.expect(data).to.be.an('array');
    const ids = data.map(row => row.id);
    pm.expect(ids).not.to.include(Number(pm.variables.get('rentalId')));
    pm.expect(ids).not.to.include(Number(pm.variables.get('rejectedRentalId')));
  });
}` }),
    request('39 Reject rental list without token', '/rentals', null, 401, { method: 'GET' }),
    request('40 Reject rental details without token', detailUrl, null, 401, { method: 'GET' }),
    request('41 Missing rental details return not found', '/rentals/9223372036854775807', null, 404, { ...renterGet,
      tests: `pm.test('Missing rental message', () => pm.expect(pm.response.text()).to.eql('Rental not found'));` }),
    ...['invalid', '0', '-1', '9223372036854775808'].map((id, index) =>
      request(`${42 + index} Reject invalid rental detail ID (${id})`, '/rentals/' + id, null, 400, { ...renterGet,
        tests: `pm.test('Invalid rental ID message', () => pm.expect(pm.response.text()).to.eql('Invalid rental ID'));` })),
    request('46 Reject car ID mismatch in rental body', createUrl, { ...rental, carId: 0 }, 400, { auth: true,
      tests: `pm.test('Car must match URL', () => pm.expect(pm.response.text()).to.eql('Car ID must match the car in the URL'));` }),
    request('47 Reject rental starting in the past', createUrl,
      { ...rental, startAt: '2000-01-01T10:00:00', endAt: '2000-01-01T12:00:00' }, 400, { auth: true }),
    request('48 Reject rental with equal start and end', createUrl, { ...rental, endAt: '{{startAt}}' }, 400, { auth: true }),
    request('49 Reject malformed rental body', createUrl, null, 400, { auth: true, rawBody: '{' }),
    request('50 Reject incomplete rental body', createUrl, { carId: '__CAR_ID__', startAt: '{{startAt}}' }, 400, { auth: true }),
    request('51 Reject invalid car ID on rental creation', '/cars/invalid/rental', rental, 400, { auth: true }),
    request('52 Reject malformed status body', statusUrl, null, 400, { ...ownerPatch, rawBody: '{' }),
    request('53 Reject missing status field', statusUrl, {}, 400, ownerPatch),
    request('54 Reject cancellation through status endpoint', statusUrl, { status: 'CANCELLED' }, 400, ownerPatch),
    request('55 Owner can get rental details', detailUrl, null, 200, { method: 'GET', auth: 'owner',
      tests: `${statusTests('CANCELLED')}
if (pm.response.code === 200) {
  const data = pm.response.json();
  pm.test('Owner receives the car summary', () => {
    pm.expect(data.car.id).to.eql(Number(pm.variables.get('carId')));
    pm.expect(data.car.brand).to.be.a('string');
    pm.expect(data.car.type).to.be.a('string');
  });
}` })
  ]
};
let nextNumber = 56;
const add = (name, url, body, status, options = {}) =>
  collection.item.push(request(`${nextNumber++} ${name}`, url, body, status, options));
add('Get own profile without exposing password', '/users/{{userId}}', null, 200, { ...renterGet,
  tests: `if (pm.response.code === 200) {
const data = pm.response.json();
pm.test('Own profile returned', () => {
  pm.expect(data.id).to.eql(Number(pm.variables.get('userId')));
  pm.expect(data.email).to.eql(pm.variables.get('email'));
  pm.expect(data.name).to.eql('Postman Test User');
  pm.expect(data.phone).to.eql('+31612345678');
  pm.expect(data.address).to.eql('Teststraat 1, Amsterdam');
});
pm.test('Password fields are excluded', () => {
  pm.expect(data).not.to.have.property('password');
  pm.expect(data).not.to.have.property('passwordHash');
});
}` });
add('Reject another user profile', '/users/{{userId}}', null, 403, { method: 'GET', auth: 'owner' });
add('Reject profile without token', '/users/{{userId}}', null, 401, { method: 'GET' });
add('Reject invalid user ID', '/users/invalid', null, 400, renterGet);
add('Logout reports current unsupported behavior', '/logout', null, 501, { auth: true,
  tests: `pm.test('Logout limitation explained', () => pm.expect(pm.response.text()).to.eql('Server-side logout is not implemented. Discard the token on the client; it remains valid until expiry.'));` });
add('Token remains valid after unsupported logout', '/users/{{userId}}', null, 200, renterGet);
for (const [label, changes, message] of [
  ['blank name', { name: '   ' }, 'Name, email, phone and address are required'],
  ['blank phone', { phone: ' ' }, 'Name, email, phone and address are required'],
  ['blank address', { address: ' ' }, 'Name, email, phone and address are required'],
  ['oversized name', { name: 'x'.repeat(101) }, 'Name, email, phone and address must be at most 100 characters'],
  ['short password', { password: 'short' }, 'Password must contain at least 8 characters'],
  ['blank password', { password: '        ' }, 'Password must contain at least 8 characters'],
  ['oversized UTF-8 password', { password: 'é'.repeat(37) }, 'Password must be at most 72 UTF-8 bytes'],
]) {
  add(`Reject registration with ${label}`, '/register', { ...user, ...changes }, 400, {
    tests: `pm.test('Validation message', () => pm.expect(pm.response.text()).to.eql(${JSON.stringify(message)}));`
  });
}
add('Reject malformed registration JSON', '/register', null, 400, { rawBody: '{' });
add('Reject missing registration fields', '/register', {}, 400);
add('Reject missing car lookup', '/cars/9223372036854775807', null, 404, { method: 'GET' });
add('Reject booking a missing car', '/cars/9223372036854775807/rental',
  rental, 404, { auth: true });
for (const field of ['startAt', 'endAt']) {
  add(`Reject malformed ${field}`, createUrl, { ...rental, [field]: 'invalid-date' }, 400, { auth: true });
}
add('Register unrelated user', '/register', { ...user, email: '{{strangerEmail}}' }, 200, {
  pre: `pm.collectionVariables.set('strangerEmail', 'postman.stranger.' + pm.variables.replaceIn('{{$guid}}') + '@example.com');
pm.collectionVariables.unset('strangerToken');`
});
add('Login unrelated user', '/login', { email: '{{strangerEmail}}', password: '{{password}}' }, 200, {
  tests: `if (pm.response.code === 200) {
const data = pm.response.json();
pm.test('Stranger token returned', () => pm.expect(data.token).to.be.a('string'));
pm.collectionVariables.set('strangerToken', data.token);
}`
});
add('Reject unrelated user reading rental', detailUrl, null, 403, { method: 'GET', auth: 'stranger' });
add('Reject unrelated user cancelling rental', cancelUrl, null, 403, { method: 'PATCH', auth: 'stranger' });
add('Reject unrelated user deciding rental', statusUrl, { status: 'CONFIRMED' }, 403, { method: 'PATCH', auth: 'stranger' });
add('Unrelated user list excludes all renter reservations', '/rentals', null, 200, { method: 'GET', auth: 'stranger',
  tests: `pm.test('Unrelated user has empty list', () => pm.expect(pm.response.json()).to.eql([]));` });
for (const [label, url, body, method] of [
  ['profile', '/users/{{userId}}', null, 'GET'],
  ['rental list', '/rentals', null, 'GET'],
  ['rental details', detailUrl, null, 'GET'],
  ['booking', createUrl, rental, 'POST'],
  ['cancellation', cancelUrl, null, 'PATCH'],
  ['owner decision', statusUrl, { status: 'CONFIRMED' }, 'PATCH'],
]) {
  add(`Reject malformed token on ${label}`, url, body, 401, { method, auth: 'invalid',
    pre: `pm.collectionVariables.set('invalidToken', 'invalid.jwt.token');` });
}
add('Reject token with tampered signature', '/rentals', null, 401, { method: 'GET', auth: 'invalid',
  pre: `const parts = pm.variables.get('token').split('.');
parts[2] = (parts[2][0] === 'A' ? 'B' : 'A') + parts[2].slice(1);
pm.collectionVariables.set('invalidToken', parts.join('.'));` });
add('Reject unavailable car booking (optional)', '/cars/{{unavailableCarId}}/rental',
  { ...rental, carId: '__UNAVAILABLE_CAR_ID__' }, 409, { auth: true,
    rawBody: JSON.stringify({ carId: '__UNAVAILABLE_CAR_ID__', startAt: '{{startAt}}', endAt: '{{endAt}}' })
      .replace('"__UNAVAILABLE_CAR_ID__"', '{{unavailableCarId}}'),
    pre: `if (!pm.variables.get('unavailableCarId')) {
  console.info('Skipping unavailable car check: set unavailableCarId to an existing car with isAvailable=false.');
  pm.execution.skipRequest();
}`,
    tests: `pm.test('Car is explicitly unavailable', () => pm.expect(pm.response.text()).to.eql('Car is unavailable'));`
  });
// Use dedicated cars so management checks never update or delete the selected booking car.
const owner = { auth: 'owner' };
const managedUrl = '/cars/{{managedCarId}}';
const carInput = { brand: 'Coverage', type: 'Hatchback', licensePlate: '{{managedPlate}}',
  pricePerHour: 12.50, pickupId: '__PICKUP__', dropOffId: '__DROPOFF__',
  category: 'ICE', fuelType: 'GASOLINE', consumptionPer100Km: 6.00 };
const carBody = changes => JSON.stringify({ ...carInput, ...changes })
  .replace('"__PICKUP__"', '{{fixturePickupId}}').replace('"__DROPOFF__"', '{{fixtureDropOffId}}');
const ownerPut = { ...owner, method: 'PUT' };
for (const [label, body] of [
  ['malformed JSON', '{'], ['missing fields', '{}'],
  ['missing password', JSON.stringify({ email: '{{email}}' })],
  ['missing email', JSON.stringify({ password: '{{password}}' })],
  ['null email', JSON.stringify({ email: null, password: '{{password}}' })],
]) add(`Reject login with ${label}`, '/login', null, 400, { rawBody: body });
add('Reject oversized UTF-8 login password', '/login', { email: '{{email}}', password: 'é'.repeat(37) }, 401);
add('Register password at bcrypt byte boundary', '/register', { ...user, email: '{{boundaryEmail}}', password: 'é'.repeat(36) }, 200, {
  pre: `pm.collectionVariables.set('boundaryEmail', 'boundary.' + pm.variables.replaceIn('{{$guid}}') + '@example.com');`
});
add('Login at bcrypt byte boundary', '/login', { email: '{{boundaryEmail}}', password: 'é'.repeat(36) }, 200);
add('Reject longer login password sharing valid 72-byte prefix', '/login',
  { email: '{{boundaryEmail}}', password: 'é'.repeat(36) + 'x' }, 401);
add('List public cars with expected fields', '/cars', null, 200, { method: 'GET', tests: `
const cars = pm.response.json();
pm.test('Selected car is listed with its fields', () => {
  pm.expect(cars).to.be.an('array');
  const car = cars.find(c => c.id === Number(pm.variables.get('carId')));
  pm.expect(car).to.be.an('object');
  for (const key of ['ownerId', 'brand', 'type', 'licensePlate', 'pricePerHour', 'pickupId', 'dropOffId', 'isAvailable']) {
    pm.expect(car).to.have.property(key);
  }
});` });
add('Create dedicated management car with normalized plate', '/cars', null, 201, { ...owner,
  pre: `pm.collectionVariables.unset('managedCarId');
pm.collectionVariables.set('managedPlate', 'pm-' + pm.variables.replaceIn('{{$guid}}'));
pm.collectionVariables.set('normalizedManagedPlate', pm.variables.get('managedPlate').toUpperCase());`,
  rawBody: carBody({ licensePlate: '  {{managedPlate}}  ' }), tests: `if (pm.response.code === 201) {
  const car = pm.response.json();
  pm.collectionVariables.set('managedCarId', car.id);
  pm.test('Plate normalized and availability defaults to false', () => {
    pm.expect(car.licensePlate).to.eql(pm.variables.get('normalizedManagedPlate'));
    pm.expect(car.isAvailable).to.eql(false);
    const payload = JSON.parse(require('buffer').Buffer.from(pm.variables.get('ownerToken').split('.')[1], 'base64').toString());
    pm.expect(car.ownerId).to.eql(payload.userId);
  });
}` });
add('Reject normalized duplicate plate on create', '/cars', null, 409, { ...owner, rawBody: carBody({ licensePlate: '{{normalizedManagedPlate}}' }) });
add('Reject car creation without token', '/cars', null, 401, { rawBody: carBody({ licensePlate: 'UNAUTHORIZED' }) });
add('Reject renter creating car', '/cars', null, 403, { auth: true, rawBody: carBody({ licensePlate: 'UNAUTHORIZED' }) });
for (const method of ['PUT', 'DELETE']) {
  add(`Reject ${method} car without token`, managedUrl, method === 'PUT' ? { brand: 'Unauthorized' } : null, 401, { method });
  add(`Reject renter ${method} car`, managedUrl, method === 'PUT' ? { brand: 'Unauthorized' } : null, 403, { method, auth: true });
}
add('Authorization failures preserve management car', managedUrl, null, 200, { method: 'GET', tests: `
pm.test('Car unchanged', () => pm.expect(pm.response.json().brand).to.eql('Coverage'));` });
for (const [label, changes] of [
  ['blank brand', { brand: ' ' }], ['blank type', { type: ' ' }], ['blank plate', { licensePlate: ' ' }],
  ['zero price', { pricePerHour: 0 }], ['negative price', { pricePerHour: -1 }],
  ['price overflow', { pricePerHour: 100000000 }], ['extra price precision', { pricePerHour: 1.001 }],
  ['missing pickup', { pickupId: 9223372036854775000 }], ['missing drop-off', { dropOffId: 9223372036854775000 }],
]) {
  add(`Reject car create with ${label}`, '/cars', null, 400, { ...owner, rawBody: carBody({ licensePlate: '{{managedPlate}}-invalid', ...changes }) });
  add(`Reject car update with ${label}`, managedUrl, changes, 400, ownerPut);
}
add('Reject malformed car creation', '/cars', null, 400, { ...owner, rawBody: '{' });
add('Reject incomplete car creation', '/cars', {}, 400, owner);
add('Reject malformed car update', managedUrl, null, 400, { ...ownerPut, rawBody: '{' });
add('Failed multi-field update rolls back all fields', managedUrl,
  { brand: 'Must roll back', dropOffId: 9223372036854775000 }, 400, ownerPut);
add('Rejected updates preserve stored fields', managedUrl, null, 200, { method: 'GET', tests: `
pm.test('Original values retained', () => {
  const car = pm.response.json();
  pm.expect(car.brand).to.eql('Coverage'); pm.expect(car.pricePerHour).to.eql(12.5);
  pm.expect(car.licensePlate).to.eql(pm.variables.get('normalizedManagedPlate'));
  pm.expect(car.dropOffId).to.eql(Number(pm.variables.get('fixtureDropOffId')));
});` });
for (const method of ['PUT', 'DELETE']) {
  add(`Reject ${method} car invalid ID`, '/cars/invalid', method === 'PUT' ? {} : null, 400, { ...owner, method });
  add(`Reject ${method} missing car`, '/cars/9223372036854775807', method === 'PUT' ? {} : null, 404, { ...owner, method });
}
add('Create second management car', '/cars', null, 201, { ...owner,
  rawBody: carBody({ licensePlate: '{{managedPlate}}-second' }), tests: `if (pm.response.code === 201) {
pm.collectionVariables.set('secondManagedCarId', pm.response.json().id);
}` });
add('Reject duplicate plate update', '/cars/{{secondManagedCarId}}', { licensePlate: '  {{managedPlate}}  ' }, 409, ownerPut);
add('Update every editable car field', managedUrl, null, 200, { ...ownerPut,
  rawBody: carBody({ brand: 'Updated', type: 'Estate', licensePlate: ' {{managedPlate}}-updated ',
    pricePerHour: 22.25, pickupId: '__DROPOFF__', dropOffId: '__PICKUP__', pictures: ['https://example.com/updated.png'], isAvailable: true }),
  tests: `if (pm.response.code === 200) {
pm.collectionVariables.set('managedCarSnapshot', JSON.stringify(pm.response.json()));
}` });
add('Read persisted car updates', managedUrl, null, 200, { method: 'GET', tests: `
const car = pm.response.json();
pm.test('All editable fields persisted', () => {
  pm.expect(car.brand).to.eql('Updated'); pm.expect(car.type).to.eql('Estate');
  pm.expect(car.licensePlate).to.eql(pm.variables.get('normalizedManagedPlate') + '-UPDATED');
  pm.expect(car.pricePerHour).to.eql(22.25);
  pm.expect(car.pickupId).to.eql(Number(pm.variables.get('fixtureDropOffId')));
  pm.expect(car.dropOffId).to.eql(Number(pm.variables.get('fixturePickupId')));
  pm.expect(car.pictures).to.eql(['https://example.com/updated.png']);
  pm.expect(car.isAvailable).to.eql(true);
});` });
add('Empty update preserves car', managedUrl, {}, 200, { ...ownerPut, tests: `
pm.test('Omitted fields preserved', () => pm.expect(pm.response.json()).to.eql(JSON.parse(pm.variables.get('managedCarSnapshot'))));` });
add('Accept maximum car price', managedUrl, { pricePerHour: 99999999.99 }, 200, { ...ownerPut,
  tests: `pm.test('Maximum price returned', () => pm.expect(pm.response.json().pricePerHour).to.eql(99999999.99));` });
add('Accept trailing decimal zeros', managedUrl, null, 200, { ...ownerPut, rawBody: '{"pricePerHour":12.5000}',
  tests: `pm.test('Exact price retained', () => pm.expect(pm.response.json().pricePerHour).to.eql(12.5));` });
add('Clear pictures and disable availability', managedUrl, { pictures: [], isAvailable: false }, 200, ownerPut);
add('Read cleared pictures and disabled availability', managedUrl, null, 200, { method: 'GET', tests: `
pm.test('Pictures and availability persisted', () => {
  pm.expect(pm.response.json().pictures).to.eql([]);
  pm.expect(pm.response.json().isAvailable).to.eql(false);
});` });
add('Calculate consumption cost as owner', managedUrl + '/consumption-cost', null, 200, {
  method: 'GET', auth: 'owner',
  tests: `const data = pm.response.json();
pm.test('Consumption cost response is complete', () => {
  pm.expect(data.carId).to.eql(Number(pm.variables.get('managedCarId')));
  pm.expect(data.category).to.eql('ICE');
  pm.expect(data.consumptionCostPerKm).to.be.a('number');
});`
});
add('Calculate TCO as owner', managedUrl + '/tco?kmPerYear=12000', null, 200, {
  method: 'GET', auth: 'owner',
  tests: `const data = pm.response.json();
pm.test('TCO response is complete', () => {
  pm.expect(data.carId).to.eql(Number(pm.variables.get('managedCarId')));
  pm.expect(data.category).to.eql('ICE');
  pm.expect(data.kmPerYear).to.eql(12000);
  pm.expect(data.tco).to.be.a('number');
});`
});
for (const query of ['', '?kmPerYear=abc', '?kmPerYear=0', '?kmPerYear=-5']) {
  add(`Reject invalid TCO query ${query || 'missing'}`, managedUrl + '/tco' + query, null, 400, { method: 'GET', auth: 'owner' });
}
add('Reject consumption cost without token', managedUrl + '/consumption-cost', null, 401, { method: 'GET' });
add('Reject TCO for renter', managedUrl + '/tco?kmPerYear=12000', null, 403, { method: 'GET', auth: true });
add('Reject booking the disabled management car', '/cars/{{managedCarId}}/rental', null, 409, { auth: true,
  rawBody: JSON.stringify({ carId: '__MANAGED__', startAt: '{{startAt}}', endAt: '{{endAt}}' }).replace('"__MANAGED__"', '{{managedCarId}}'),
  tests: `pm.test('Unavailable car reason', () => pm.expect(pm.response.text()).to.eql('Car is unavailable'));` });
add('Reject deleting car with rental history', '/cars/{{carId}}', null, 409, { ...owner, method: 'DELETE',
  tests: `pm.test('History protected', () => pm.expect(pm.response.json().error).to.eql('Car has associated rentals and cannot be deleted'));` });
for (const variable of ['managedCarId', 'secondManagedCarId']) {
  add(`Delete dedicated car ${variable}`, '/cars/{{' + variable + '}}', null, 204, { ...owner, method: 'DELETE' });
  add(`Deleted car ${variable} is absent`, '/cars/{{' + variable + '}}', null, 404, { method: 'GET' });
}

// A second owner's credentials cannot be created by the public registration API.
const optionalOtherOwner = `if (!pm.variables.get('otherOwnerEmail') || !pm.variables.get('otherOwnerPassword')) {
  console.info('Skipping unrelated-owner checks: configure otherOwnerEmail and otherOwnerPassword.');
  pm.execution.skipRequest();
}`;
add('Login unrelated owner (optional)', '/login', { email: '{{otherOwnerEmail}}', password: '{{otherOwnerPassword}}' }, 200, {
  pre: `pm.collectionVariables.unset('otherOwnerToken');\n` + optionalOtherOwner,
  tests: `if (pm.response.code === 200) {
pm.test('Account is an owner', () => pm.expect(pm.response.json().role).to.eql('OWNER'));
pm.collectionVariables.set('otherOwnerToken', pm.response.json().token);
}` });
for (const [label, url, method, body] of [
  ['deciding rental', statusUrl, 'PATCH', { status: 'CONFIRMED' }],
  ['reading rental', detailUrl, 'GET', null],
  ['updating car', '/cars/{{carId}}', 'PUT', { brand: 'Unauthorized' }],
  ['deleting car', '/cars/{{carId}}', 'DELETE', null],
]) {
  const item = request(`${nextNumber++} Reject unrelated owner ${label} (optional)`, url, body, 403,
    { method, auth: 'owner', pre: optionalOtherOwner });
  item.request.auth.bearer[0].value = '{{otherOwnerToken}}';
  collection.item.push(item);
}
for (const key of ['otherOwnerEmail', 'otherOwnerPassword', 'otherOwnerToken']) {
  collection.variable.push({ key, value: '', type: 'string' });
}
// Shared input validation regressions. These use a separate disposable car.
const validationUrl = '/cars/{{validationCarId}}';
add('Create validation fixture car', '/cars', null, 201, { ...owner,
  rawBody: carBody({ licensePlate: '{{managedPlate}}-validation' }), tests: `if (pm.response.code === 201) {
pm.collectionVariables.set('validationCarId', pm.response.json().id);
pm.collectionVariables.set('validationCarSnapshot', JSON.stringify(pm.response.json()));
}` });
for (const [label, changes] of [
  ['oversized brand', { brand: 'a'.repeat(256) }], ['oversized type', { type: 'a'.repeat(256) }],
  ['oversized plate', { licensePlate: 'A'.repeat(256) }],
  ['plate exceeding limit after uppercasing', { licensePlate: 'ß'.repeat(128) }],
  ['invalid plate syntax', { licensePlate: 'AB/<script>' }],
  ['control character in brand', { brand: 'Ford\u0000Test' }],
  ['zero pickup ID', { pickupId: 0 }], ['negative drop-off ID', { dropOffId: -1 }],
  ['too many pictures', { pictures: Array(21).fill('https://example.com/car.png') }],
  ['oversized picture URL', { pictures: ['https://example.com/'.padEnd(2049, 'a')] }],
  ['blank picture URL', { pictures: [' '] }], ['relative picture URL', { pictures: ['relative.png'] }],
  ['unsupported picture scheme', { pictures: ['javascript:alert(1)'] }],
  ['picture URL without host', { pictures: ['https:///car.png'] }],
  ['picture URL with credentials', { pictures: ['https://user:password@example.com/car.png'] }],
  ['picture URL with invalid port', { pictures: ['https://example.com:70000/car.png'] }],
]) {
  add(`Reject car create with ${label}`, '/cars', null, 400, { ...owner,
    rawBody: carBody({ licensePlate: '{{managedPlate}}-invalid', ...changes }) });
  add(`Reject car update with ${label}`, validationUrl, changes, 400, ownerPut);
}
add('Validation failures leave car unchanged', validationUrl, null, 200, { method: 'GET', tests: `
pm.test('All original fields preserved', () => pm.expect(pm.response.json()).to.eql(JSON.parse(pm.variables.get('validationCarSnapshot'))));` });
add('Accept maximum text and picture limits', validationUrl,
  { brand: 'a'.repeat(255), type: 'b'.repeat(255), licensePlate: 'C'.repeat(255),
    pictures: Array(20).fill('https://example.com/'.padEnd(2048, 'a')) }, 200, ownerPut);
add('Boundary values survive database round trip', validationUrl, null, 200, { method: 'GET', tests: `
pm.test('Maximum text and picture values persisted', () => {
  const car = pm.response.json();
  pm.expect(car.brand).to.eql('a'.repeat(255)); pm.expect(car.type).to.eql('b'.repeat(255));
  pm.expect(car.licensePlate).to.eql('C'.repeat(255));
  pm.expect(car.pictures).to.eql(Array(20).fill('https://example.com/'.padEnd(2048, 'a')));
});` });
add('Delete validation fixture car', validationUrl, null, 204, { ...owner, method: 'DELETE' });
add('Deleted validation fixture is absent', validationUrl, null, 404, { method: 'GET' });
for (const [label, changes] of [
  ['phone containing letters', { phone: 'call me please' }], ['short phone', { phone: '123456' }],
  ['long phone', { phone: '1'.repeat(16) }], ['multiple phone plus signs', { phone: '++31612345678' }],
  ['unbalanced phone parentheses', { phone: '+31 (612345678' }],
  ['contact control character', { name: 'Test\u0000User' }],
]) add(`Reject registration with ${label}`, '/register', { ...user, ...changes }, 400);
add('Register formatted international phone', '/register', { ...user, email: '{{formattedPhoneEmail}}', phone: '+31 (0)6 1234-5678' }, 200, {
  pre: `pm.collectionVariables.set('formattedPhoneEmail', 'phone.' + pm.variables.replaceIn('{{$guid}}') + '@example.com');` });
fs.writeFileSync(path.join(__dirname, 'RentMyCar.postman_collection.json'), JSON.stringify(collection, null, 2) + '\n');
