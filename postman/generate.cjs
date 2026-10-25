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
    request('01 Register a new user', '/register', user, 201, { pre: `pm.collectionVariables.set('email', 'postman.' + pm.variables.replaceIn('{{$guid}}') + '@example.com');
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
  pm.test('Correct car', () => pm.expect(data.id).to.eql(Number(pm.variables.get('carId'))));
  pm.test('Car is available', () => pm.expect(data.isAvailable).to.eql(true));
  pm.test('Hourly price is valid', () => pm.expect(data.pricePerHour).to.be.a('number').and.at.least(0));
  pm.collectionVariables.set('pricePerHourCents', Math.round(data.pricePerHour * 100));
}` }),
    request('04 Create rental through car route', createUrl, rental, 201, { auth: true, pre: `const start = new Date(Date.now() + 7 * 86400000);
start.setUTCMilliseconds(0);
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
    request('08 Reject duplicate registration', '/register', user, 409),
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
add('Register unrelated user', '/register', { ...user, email: '{{strangerEmail}}' }, 201, {
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
fs.writeFileSync(path.join(__dirname, 'RentMyCar.postman_collection.json'), JSON.stringify(collection, null, 2) + '\n');
