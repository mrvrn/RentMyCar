const fs = require('node:fs');
const path = require('node:path');
const event = (listen, code) => ({ listen, script: { type: 'text/javascript', exec: code.split('\n') } });
function request(name, url, body, status, options = {}) {
  const request = { method: options.method || 'POST', url: '{{baseUrl}}' + url,
    auth: options.auth ? { type: 'bearer', bearer: [{ key: 'token', value: '{{token}}', type: 'string' }] } : { type: 'noauth' }, header: [] };
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
const collection = {
  info: { name: 'RentMyCar API tests', schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json', description: 'Run in order. Requires an existing available car. Creates persistent users and rentals. See the accompanying README.' },
  variable: Object.entries({ baseUrl: 'http://localhost:8080', carId: '1', email: '', password: 'PostmanTest123!', token: '', startAt: '', endAt: '', rentalId: '' }).map(([key, value]) => ({ key, value, type: 'string' })),
  item: [
    request('01 Register a new user', '/register', user, 201, { pre: `pm.collectionVariables.set('email', 'postman.' + pm.variables.replaceIn('{{$guid}}') + '@example.com');
pm.collectionVariables.unset('token');
pm.collectionVariables.unset('rentalId');` }),
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
    request('10 Reject invalid registration', '/register', { ...user, email: 'invalid-email' }, 400)
  ]
};
fs.writeFileSync(path.join(__dirname, 'RentMyCar.postman_collection.json'), JSON.stringify(collection, null, 2) + '\n');
