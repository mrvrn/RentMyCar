const fs = require('node:fs');
const path = require('node:path');
const event = (listen, code) => ({ listen, script: { type: 'text/javascript', exec: code.split('\n') } });
function request(name, url, body, status, options = {}) {
  const request = { method: options.method || 'POST', url: '{{baseUrl}}' + url,
    auth: options.auth ? { type: 'bearer', bearer: [{ key: 'token', value: options.auth === 'owner' ? '{{ownerToken}}' : '{{token}}', type: 'string' }] } : { type: 'noauth' }, header: [] };
  if (body) {
    request.header.push({ key: 'Content-Type', value: 'application/json' });
    request.body = { mode: 'raw', raw: JSON.stringify(body, null, 2).replace('"__CAR_ID__"', '{{carId}}'), options: { raw: { language: 'json' } } };
  }
  return { name, request, event: [
    ...(options.pre ? [event('prerequest', options.pre)] : []),
    event('test', `pm.test('Status is ${status}', () => pm.response.to.have.status(${status}));\n` + (options.tests || ''))
  ] };
}
const user = { name: 'Postman Test User', email: '{{email}}', phone: '+31612345678', address: 'Teststraat 1, Amsterdam', password: '{{password}}' };
const rental = { carId: '__CAR_ID__', startAt: '{{startAt}}', endAt: '{{endAt}}' };
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
  variable: Object.entries({ baseUrl: 'http://localhost:8080', carId: '1', email: '', password: 'PostmanTest123!', token: '', ownerEmail: '', ownerPassword: '', ownerToken: '', startAt: '', endAt: '', rentalId: '', rejectedRentalId: '', cancelledAt: '', activeRentalId: '', completedRentalId: '' }).map(([key, value]) => ({ key, value, type: 'string' })),
  item: [
    request('01 Register a new user', '/register', user, 201, { pre: `pm.collectionVariables.set('email', 'postman.' + pm.variables.replaceIn('{{$guid}}') + '@example.com');
pm.collectionVariables.unset('token');
pm.collectionVariables.unset('ownerToken');
pm.collectionVariables.unset('rentalId');
pm.collectionVariables.unset('rejectedRentalId');
pm.collectionVariables.unset('cancelledAt');` }),
    request('02 Login and save token', '/login', { email: '{{email}}', password: '{{password}}' }, 200, { pre: `pm.collectionVariables.unset('token');`, tests: `if (pm.response.code === 200) {
  const data = pm.response.json();
  pm.test('JWT returned', () => pm.expect(data.token).to.be.a('string').and.match(/^[^.]+\\.[^.]+\\.[^.]+$/));
  if (typeof data.token === 'string') pm.collectionVariables.set('token', data.token);
}` }),
    request('03 Check selected car', '/cars/{{carId}}', null, 200, { method: 'GET', tests: `if (pm.response.code === 200) {
  const data = pm.response.json();
  pm.test('Correct car', () => pm.expect(data.id).to.eql(Number(pm.variables.get('carId'))));
  pm.test('Car is available', () => pm.expect(data.isAvailable).to.eql(true));
}` }),
    request('04 Create rental', '/rentals/{{carId}}', rental, 201, { auth: true, pre: `const start = new Date(Date.now() + 7 * 86400000);
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
  pm.collectionVariables.set('rentalId', data.id);
}` }),
    request('05 Reject overlapping rental', '/rentals/{{carId}}', rental, 409, { auth: true }),
    request('06 Reject rental without token', '/rentals/{{carId}}', rental, 401),
    request('07 Reject reversed dates', '/rentals/{{carId}}', { ...rental, startAt: '{{endAt}}', endAt: '{{startAt}}' }, 400, { auth: true }),
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
    request('20 Rebook rejected period', '/rentals/{{carId}}', rental, 201, { auth: true,
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
    request('31 Rebook cancelled period', '/rentals/{{carId}}', rental, 201, {
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
      optionalCancellation('completedRentalId', 'COMPLETED'))
  ]
};
fs.writeFileSync(path.join(__dirname, 'RentMyCar.postman_collection.json'), JSON.stringify(collection, null, 2) + '\n');
